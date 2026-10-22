package me.ash.reader.domain.service

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.ash.reader.domain.data.SyncLogger
import me.ash.reader.domain.model.account.AccountType
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.feed.FeedWithArticle
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.infrastructure.android.NotificationHelper
import me.ash.reader.infrastructure.di.DefaultDispatcher
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.rss.RssHelper
import timber.log.Timber

private const val TAG = "LocalRssService"

private class FeedSyncException(feed: Feed, cause: Throwable) :
    Exception("${feed.name}: ${cause.message ?: cause::class.java.simpleName}", cause)

class LocalRssService
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val rssHelper: RssHelper,
    private val notificationHelper: NotificationHelper,
    private val groupDao: GroupDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val workManager: WorkManager,
    private val accountService: AccountService,
    private val syncLogger: SyncLogger,
) :
    AbstractRssRepository(
        articleDao,
        groupDao,
        feedDao,
        workManager,
        rssHelper,
        notificationHelper,
        ioDispatcher,
        defaultDispatcher,
        accountService,
    ) {

    override suspend fun sync(
        accountId: Int,
        feedId: String?,
        groupId: String?,
        progress: SyncProgressSession?,
    ): ListenableWorker.Result = supervisorScope {
        return@supervisorScope runCatching {
            val preTime = System.currentTimeMillis()
            val preDate = Date(preTime)
            val currentAccount = accountService.getAccountById(accountId)!!
            require(currentAccount.type.id == AccountType.Local.id) {
                "Account type is invalid"
            }
            val semaphore = Semaphore(16)

            val feedsToSync =
                when {
                    feedId != null -> listOfNotNull(feedDao.queryById(feedId))
                    groupId != null -> feedDao.queryByGroupId(accountId, groupId)
                    else -> feedDao.queryAll(accountId)
                }

            progress?.setFeedTotal(feedsToSync.size)

            feedsToSync
                .mapIndexed { _, currentFeed ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                val archivedArticles =
                                    feedDao
                                        .queryArchivedArticles(currentFeed.id)
                                        .map { it.link }
                                        .toSet()
                                val fetchedFeed = syncFeed(currentFeed, preDate)
                                val fetchedArticles =
                                    fetchedFeed.articles.filterNot {
                                        archivedArticles.contains(it.link)
                                    }

                                val newArticles =
                                    articleDao.insertListIfNotExist(
                                        articles = fetchedArticles,
                                        feed = currentFeed,
                                    )
                                progress?.feedCompleted(newArticles.size)
                                if (currentFeed.isNotification && newArticles.isNotEmpty()) {
                                    notificationHelper.notify(
                                        fetchedFeed.copy(articles = newArticles, feed = currentFeed)
                                    )
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                throw FeedSyncException(currentFeed, e)
                            }
                        }
                    }
                }
                .awaitAll()

            Timber.tag("RlOG").i("onCompletion: ${System.currentTimeMillis() - preTime}")
            accountService.update(currentAccount.copy(updateAt = Date()))
            ListenableWorker.Result.success()
        }
            .onFailure {
                if (it is CancellationException) throw it
                syncLogger.log(it)
                progress?.setError(it.message ?: it::class.java.simpleName)
            }
            .getOrNull() ?: ListenableWorker.Result.retry()
    }

    private suspend fun syncFeed(feed: Feed, preDate: Date = Date()): FeedWithArticle {
        val articles = rssHelper.queryRssXml(feed, "", preDate)
        // Older versions trusted a declared /favicon.ico even when the site
        // answers 204 No Content. Re-check only those legacy icons; new
        // subscriptions already resolve a working icon when they are added.
        val icon = feed.icon
        val needsIconRefresh =
            icon == null ||
                (icon.substringBefore('?').endsWith("/favicon.ico", ignoreCase = true) &&
                    !rssHelper.isUsableIcon(icon))
        if (needsIconRefresh) {
            val iconLink = rssHelper.queryRssIcon(feed.url)
            if (iconLink != null) {
                rssHelper.saveRssIcon(feedDao, feed, iconLink)
            }
        }
        return FeedWithArticle(
            feed = feed.copy(isNotification = feed.isNotification && articles.isNotEmpty()),
            articles = articles,
        )
    }
}
