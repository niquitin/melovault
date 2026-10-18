package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

/** Prints timings for 28k synthetic accented songs; assertions are deliberately loose. */
class TelegramSearchPerformanceTest {

    @Test
    fun backfillAndSearch28kSongs() {
        val n = 28_000
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            c.createStatement().use {
                it.execute(
                    "CREATE TABLE telegram_songs (account_id INTEGER NOT NULL, chat_id INTEGER NOT NULL, " +
                        "message_id INTEGER NOT NULL, title TEXT NOT NULL, filename TEXT NOT NULL, performer TEXT, " +
                        "date INTEGER NOT NULL DEFAULT 0, is_deleted INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY (account_id, chat_id, message_id))"
                )
            }
            c.autoCommit = false
            c.prepareStatement(
                "INSERT INTO telegram_songs (account_id,chat_id,message_id,title,filename,performer,date) VALUES (1,2,?,?,?,?,?)"
            ).use { ps ->
                for (i in 1..n) {
                    ps.setLong(1, i.toLong())
                    ps.setString(2, "Bài hát số $i Sử Thanh Hóa mix")
                    ps.setString(3, "bai_hat_$i.mp3")
                    ps.setString(4, "Ca sĩ ${i % 50}")
                    ps.setLong(5, i.toLong())
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            c.commit()

            val t0 = System.nanoTime()
            c.createStatement().use { it.execute(TelegramLibrarySql.MIGRATE_1_TO_2_ADD_COLUMN) }
            val rows = mutableListOf<Array<Any?>>()
            c.createStatement().use { st ->
                st.executeQuery("SELECT message_id,title,performer,filename FROM telegram_songs").use { rs ->
                    while (rs.next()) rows.add(arrayOf(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)))
                }
            }
            c.prepareStatement(
                "UPDATE telegram_songs SET search_key=? WHERE account_id=1 AND chat_id=2 AND message_id=?"
            ).use { ps ->
                for (r in rows) {
                    ps.setString(1, TelegramSearchPolicy.songSearchKey(r[1] as String, r[2] as String?, r[3] as String))
                    ps.setLong(2, r[0] as Long)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            c.commit()
            val backfillMs = (System.nanoTime() - t0) / 1_000_000
            println("PERF backfill rows=$n ms=$backfillMs")
            assertTrue("backfill too slow: $backfillMs", backfillMs < 10_000)

            val where = TelegramSearchPolicy.whereClause("su thanh hoa 777")!!
            var worst = 0L
            repeat(20) {
                val s = System.nanoTime()
                var got = 0
                c.prepareStatement(
                    "SELECT * FROM telegram_songs WHERE account_id=1 AND is_deleted=0 AND ${where.sql} ORDER BY date DESC LIMIT 200"
                ).use { ps ->
                    where.args.forEachIndexed { i, a -> ps.setString(i + 1, a) }
                    ps.executeQuery().use { rs -> while (rs.next()) got++ }
                }
                c.prepareStatement(
                    "SELECT COUNT(*) FROM telegram_songs WHERE account_id=1 AND is_deleted=0 AND ${where.sql}"
                ).use { ps ->
                    where.args.forEachIndexed { i, a -> ps.setString(i + 1, a) }
                    ps.executeQuery().use { rs -> rs.next(); assertEquals(got, rs.getInt(1)) }
                }
                worst = maxOf(worst, (System.nanoTime() - s) / 1_000_000)
            }
            println("PERF search worst_ms=$worst")
            assertTrue("search too slow: $worst", worst < 1_000)
        }
    }
}
