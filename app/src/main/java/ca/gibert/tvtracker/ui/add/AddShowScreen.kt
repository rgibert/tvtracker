package ca.gibert.tvtracker.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gibert.tvtracker.data.AppContainer
import ca.gibert.tvtracker.data.Show
import ca.gibert.tvtracker.data.withNetworkOverride
import ca.gibert.tvtracker.ui.components.EmptyState
import ca.gibert.tvtracker.ui.components.Pill
import ca.gibert.tvtracker.ui.components.PosterImage
import ca.gibert.tvtracker.ui.components.ServiceLine
import ca.gibert.tvtracker.ui.theme.GlassSurface
import ca.gibert.tvtracker.ui.theme.ZenIconButton

@Composable
fun AddShowScreen(
    container: AppContainer,
    onOpenShow: (Int) -> Unit,
    viewModel: AddShowViewModel = viewModel(factory = AddShowViewModel.factory(container)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AddShowEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    // Open straight into typing when the screen is new; coming back from a
    // show's page keeps whatever the user had already searched.
    LaunchedEffect(Unit) {
        if (state.query.isEmpty()) focusRequester.requestFocus()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search Trakt for a show") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        viewModel.searchNow()
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
            )

            Box(Modifier.fillMaxSize()) {
                when {
                    state.isSearching && state.results.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    state.errorMessage != null -> {
                        EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = "Search didn't work",
                            message = state.errorMessage.orEmpty(),
                            action = {
                                Button(onClick = viewModel::searchNow) { Text("Try again") }
                            },
                        )
                    }

                    state.searchedQuery == null -> {
                        EmptyState(
                            icon = Icons.Outlined.Search,
                            title = "Add a show",
                            message = "Search by title. Shows you add go on your Trakt " +
                                "watchlist and appear in To Watch and Coming Up.",
                        )
                    }

                    state.results.isEmpty() -> {
                        EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = "No shows found",
                            message = "Nothing on Trakt matches \"${state.searchedQuery}\". " +
                                "Try a shorter or different title.",
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 24.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                items = state.results,
                                key = { it.ids.trakt },
                            ) { show ->
                                val id = show.ids.trakt
                                SearchResultCard(
                                    show = show.withNetworkOverride(state.networkOverrides),
                                    isQueued = id in state.queuedIds,
                                    isAdding = id in state.addingIds,
                                    onClick = { onOpenShow(id) },
                                    onAdd = { viewModel.add(show) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }

                // Keep earlier results on screen while a refined query loads.
                if (state.isSearching && state.results.isNotEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .align(Alignment.TopCenter),
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun SearchResultCard(
    show: Show,
    isQueued: Boolean,
    isAdding: Boolean,
    onClick: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PosterImage(
                show = show,
                modifier = Modifier
                    .width(64.dp)
                    .height(96.dp),
                cornerRadius = 14.dp,
            )

            Spacer(Modifier.width(13.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = show.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                ServiceLine(
                    network = show.network,
                    prefix = show.year?.toString(),
                )
                val overview = show.overview?.trim()
                if (!overview.isNullOrEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isQueued) {
                    Spacer(Modifier.height(8.dp))
                    Pill("In your queue")
                }
            }

            Spacer(Modifier.width(8.dp))

            ZenIconButton(
                onClick = onAdd,
                enabled = !isQueued && !isAdding,
            ) {
                when {
                    isAdding -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.onSurface,
                    )

                    isQueued -> Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "${show.title} is in your queue",
                        tint = colors.onSurface,
                        modifier = Modifier.size(20.dp),
                    )

                    else -> Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add ${show.title} to watchlist",
                        tint = colors.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
