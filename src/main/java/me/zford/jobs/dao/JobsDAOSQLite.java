/**
 * Jobs Plugin for Bukkit
 * Copyright (C) 2011 Zak Ford <zak.j.ford@gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.zford.jobs.dao;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import me.zford.jobs.Jobs;
import me.zford.jobs.util.UUIDFetcher;
import me.zford.jobs.util.UUIDUtil;

public class JobsDAOSQLite extends JobsDAO {
    public static JobsDAOSQLite initialize() {
        JobsDAOSQLite dao = new JobsDAOSQLite();
        File dir = Jobs.getDataFolder();
        if (!dir.exists())
            dir.mkdirs();
        try {
            dao.setUp();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return dao;
    }
    
    private JobsDAOSQLite() {
        super("org.sqlite.JDBC", "jdbc:sqlite:"+new File(Jobs.getDataFolder(), "jobs.sqlite.db").getPath(), null, null, "");
    }

    @Override
    protected synchronized void setupConfig() throws SQLException {
        JobsConnection conn = getConnection();
        if (conn == null) {
            Jobs.getPluginLogger().severe("Could not run database updates!  Could not connect to MySQL!");
            return;
        }
        PreparedStatement prest = null;
        int rows = 0;
        try {
            // Check for config table
            prest = conn.prepareStatement("SELECT COUNT(*) FROM sqlite_master WHERE name = ?;");
            prest.setString(1, getPrefix()+"config");
            ResultSet res = prest.executeQuery();
            if (res.next()) {
                rows = res.getInt(1);
            }
        } finally {
            if (prest != null) {
                try {
                    prest.close();
                } catch (SQLException e) {}
            }
        }
        
        if (rows == 0) {
            PreparedStatement insert = null;
            try {
                executeSQL("CREATE TABLE `" + getPrefix() + "config` (`key` varchar(50) NOT NULL PRIMARY KEY, `value` varchar(100) NOT NULL);");
                
                insert = conn.prepareStatement("INSERT INTO `" + getPrefix() + "config` (`key`, `value`) VALUES (?, ?);");
                insert.setString(1, "version");
                insert.setString(2, "1");
                insert.execute();
            } finally {
                if (insert != null) {
                    try {
                        insert.close();
                    } catch (SQLException e) {}
                }
            }
        }
    }
    
    @Override
    protected synchronized void checkUpdate1() throws SQLException {
        JobsConnection conn = getConnection();
        if (conn == null) {
            Jobs.getPluginLogger().severe("Could not run database updates!  Could not connect to MySQL!");
            return;
        }
        PreparedStatement prest = null;
        int rows = 0;
        try {
            // Check for jobs table
            prest = conn.prepareStatement("SELECT COUNT(*) FROM sqlite_master WHERE name = ?;");
            prest.setString(1, getPrefix()+"jobs");
            ResultSet res = prest.executeQuery();
            if (res.next()) {
                rows = res.getInt(1);
            }
        } finally {
            if (prest != null) {
                try {
                    prest.close();
                } catch (SQLException e) {}
            }
        }
        
        PreparedStatement pst1 = null;
        PreparedStatement pst2 = null;
        try {
            if (rows > 0) {
                Jobs.getPluginLogger().info("Converting existing usernames to Mojang UUIDs.  This could take a long time!");
                executeSQL("ALTER TABLE `" + getPrefix() + "jobs` RENAME TO `" + getPrefix() + "jobs_old`;");
                executeSQL("ALTER TABLE `" + getPrefix() + "jobs_old` ADD COLUMN `player_uuid` binary(16) DEFAULT NULL;");
            }
            
            executeSQL("CREATE TABLE `" + getPrefix() + "jobs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `player_uuid` binary(16) NOT NULL, `job` varchar(20), `experience` int, `level` int);");
            
            if (rows > 0) {
                pst1 = conn.prepareStatement("SELECT DISTINCT `username` FROM `" + getPrefix() + "jobs_old` WHERE `player_uuid` IS NULL;");
                ResultSet rs = pst1.executeQuery();
                ArrayList<String> usernames = new ArrayList<String>();
                while (rs.next()) {
                    usernames.add(rs.getString(1));
                }
                UUIDFetcher uuidFetcher = new UUIDFetcher(usernames);
                Map<String, UUID> userMap = null;
                try {
                    userMap = uuidFetcher.call();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                if (userMap == null) {
                    Jobs.getPluginLogger().severe("Error fetching UUIDs from Mojang.  Aborting conversion!");
                    return;
                }
                pst2 = conn.prepareStatement("UPDATE `" + getPrefix() + "jobs_old` SET `player_uuid` = ? WHERE `username` = ?;");
                for (Map.Entry<String, UUID> entry : userMap.entrySet()) {
                    String username = entry.getKey();
                    UUID uuid = entry.getValue();
                    pst2.setBytes(1, UUIDUtil.toBytes(uuid));
                    pst2.setString(2, username);
                    pst2.execute();
                }
                
                executeSQL("INSERT INTO `" + getPrefix() + "jobs` (`player_uuid`, `job`, `experience`, `level`) SELECT `player_uuid`, `job`, `experience`, `level` FROM `" + getPrefix() + "jobs_old`;");
            }
        } finally {
            if (pst1 != null) {
                try {
                    pst1.close();
                } catch (SQLException e) {}
            }
            if (pst2 != null) {
                try {
                    pst2.close();
                } catch (SQLException e) {}
            }
        }
        
        if (rows > 0) {
            executeSQL("DROP TABLE `" + getPrefix() + "jobs_old`;");
            
            Jobs.getPluginLogger().info("Mojang UUID conversion complete!");
        }
    }
}
