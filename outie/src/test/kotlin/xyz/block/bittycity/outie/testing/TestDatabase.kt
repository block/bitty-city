package xyz.block.bittycity.outie.testing

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testcontainers.containers.MySQLContainer

/**
 * Test database management using Testcontainers.
 * A MySQL container is started once and reused across all tests for performance.
 */
object TestDatabase {

  /**
   * Testcontainers MySQL container that will be started once and reused.
   */
  val container: MySQLContainer<*> by lazy {
    MySQLContainer("mysql:8.0")
      .withDatabaseName("bitty_city")
      .withUsername("test")
      .withPassword("test")
      .apply {
        start()
      }
  }

  val datasource: HikariDataSource by lazy {
    HikariDataSource(HikariConfig().apply {
      jdbcUrl = container.jdbcUrl
      username = container.username
      password = container.password
      driverClassName = container.driverClassName
      maximumPoolSize = 10
    }).also(::runMigrations)
  }

  private fun runMigrations(dataSource: DataSource) {
    Flyway.configure()
      .dataSource(dataSource)
      .locations("classpath:migrations")
      .load()
      .migrate()
  }


  val dslContext: DSLContext by lazy { DSL.using(datasource, SQLDialect.MYSQL) }

  /**
   * Truncate all tables in the test database.
   * This should be called before each test to ensure a clean state.
   */
  fun truncateTables() {
    datasource.connection.use { conn ->
      val statement = conn.createStatement()

      // Disable foreign key checks to allow truncation
      statement.execute("SET FOREIGN_KEY_CHECKS = 0")

      // Get all tables in the database
      val tables = mutableListOf<String>()
      val rs = statement.executeQuery(
        "SELECT table_name FROM information_schema.tables " +
          "WHERE table_schema = '${container.databaseName}' " +
          "AND table_type = 'BASE TABLE'"
      )
      while (rs.next()) {
        tables.add(rs.getString(1))
      }

      // Truncate all tables
      tables.forEach { table ->
        statement.execute("TRUNCATE TABLE `$table`")
      }

      // Re-enable foreign key checks
      statement.execute("SET FOREIGN_KEY_CHECKS = 1")
    }
  }

  /**
   * Close the container and datasource.
   * Should be called after all tests complete (typically in a JUnit extension).
   */
  fun shutdown() {
    datasource.close()
    container.stop()
  }
}
