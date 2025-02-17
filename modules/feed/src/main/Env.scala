package lila.feed

import com.softwaremill.macwire.*

import lila.Lila.*
import lila.common.config.CollName

@Module
final class Env(cacheApi: lila.memo.CacheApi, db: lila.db.Db)(using Executor):

  private val feedColl = db(CollName("daily_feed"))
  val api              = wire[FeedApi]
  val paginator        = wire[FeedPaginatorBuilder]

  export api.lastUpdate
