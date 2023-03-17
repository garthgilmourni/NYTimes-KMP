package io.github.xxfast.nytimes.screens.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.xxfast.kstore.KStore
import io.github.xxfast.nytimes.api.NyTimesWebService
import io.github.xxfast.nytimes.models.Article
import io.github.xxfast.nytimes.models.ArticleUri
import io.github.xxfast.nytimes.models.SavedArticles
import io.github.xxfast.nytimes.models.TopStorySection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun StoryDomain(
  section: TopStorySection,
  uri: ArticleUri,
  title: String,
  initialState: StoryState,
  events: Flow<StoryEvent>,
  webService: NyTimesWebService,
  store: KStore<SavedArticles>,
): StoryState {
  var article: Article? by remember { mutableStateOf(initialState.article) }
  var refreshes: Int by remember { mutableStateOf(0) }

  val isSaved: Boolean? by store.updates
    .map { articles -> articles?.any { article -> article.uri == uri } }
    .collectAsState(DontKnowYet)

  LaunchedEffect(refreshes) {
    // Don't autoload the stories when restored from process death
    if (refreshes == 0 && article != Loading) return@LaunchedEffect

    article = Loading

    // Get the article from store, if not found get a fresh one
    article = store.get().orEmpty()
      .find { article -> article.uri == uri }
      ?: webService.story(section, uri).getOrNull()
  }

  LaunchedEffect(Unit) {
    events.collect { event ->
      when (event) {
        StoryEvent.Refresh -> refreshes++

        StoryEvent.Save -> launch(Dispatchers.Unconfined) {
          store.update { articles ->
            val articleToSave: Article? = article
            when {
              articleToSave != null && isSaved == false -> articles?.plus(articleToSave)
              articleToSave != null && isSaved == true -> articles?.minus(articleToSave)
              else -> articles
            }
          }
        }
      }
    }
  }

  return StoryState(title, article, isSaved)
}
