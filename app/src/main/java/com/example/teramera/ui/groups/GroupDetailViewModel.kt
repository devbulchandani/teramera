package com.example.teramera.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teramera.data.repository.GroupDetail
import com.example.teramera.data.repository.GroupDetailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: GroupDetailRepository,
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    val detail: StateFlow<GroupDetail?> = repository.groupDetail(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
