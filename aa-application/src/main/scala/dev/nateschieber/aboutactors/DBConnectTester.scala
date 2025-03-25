package dev.nateschieber.aboutactors

import io.r2dbc.pool.{ConnectionPool, ConnectionPoolConfiguration}
import io.r2dbc.postgresql.{PostgresqlConnectionConfiguration, PostgresqlConnectionFactory}
import reactor.core.publisher.Mono

class DBConnectTester {

  def run(): Unit = {
    val connectionFactory = new PostgresqlConnectionFactory(
      PostgresqlConnectionConfiguration.builder()
        .host("localhost")
        .port(5434)
        .username("postgres")
        .password("postgres")
        .database("postgres")
        .build()
    )

    // Create connection pool
    val connectionPool = new ConnectionPool(
      ConnectionPoolConfiguration.builder(connectionFactory)
        .initialSize(5)
        .maxSize(10)
        .build()
    )

    // Test connection
    Mono.from(connectionPool.create())
      .flatMap(connection => {
        println("Connected successfully!")
        Mono.just(connection)
          .doFinally(_ => connection.close())
      })
      .doOnError(error => {
        println(s"Failed to connect: ${error.getMessage}")
        System.exit(1) // Exit application on connection failure
      })
      .block() // Block for testing purposes only
  }

}
