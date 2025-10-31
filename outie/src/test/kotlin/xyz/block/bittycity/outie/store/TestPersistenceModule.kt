package xyz.block.bittycity.outie.store

import com.google.inject.AbstractModule
import com.google.inject.Provides
import jakarta.inject.Named
import jakarta.inject.Singleton
import javax.sql.DataSource
import org.jooq.DSLContext
import xyz.block.bittycity.outie.testing.TestDatabase

class TestPersistenceModule : AbstractModule() {

  companion object {
    const val DATASOURCE = "bitty_city"
  }

  @Provides
  @Singleton
  @Named(DATASOURCE)
  fun provideDataSource(): DataSource = TestDatabase.datasource

  @Provides
  @Singleton
  @Named(DATASOURCE)
  fun provideDslContext(): DSLContext = TestDatabase.dslContext
}
