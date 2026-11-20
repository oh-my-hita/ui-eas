package com.stupidtree.hita.ui.eas

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.stupidtree.component.data.DataState
import com.stupidtree.hita.api.ComposeUiPlugin
import com.stupidtree.hita.api.ExamService
import com.stupidtree.hita.api.PluginUiHost
import com.stupidtree.hitax.data.model.eas.ExamItem

class EasUiPlugin : ComposeUiPlugin {
    override val id = "com.stupidtree.hita.ui.eas"
    override val displayName = "教务"
    override val requiresApi = 7

    override fun createView(context: Context, host: PluginUiHost): View = ComposeView(context).apply {
        setContent { MaterialTheme { EasScreen(host.service(ExamService.KEY)) } }
    }
}

@Composable
private fun EasScreen(service: ExamService?) {
    if (service == null) {
        Text("未安装 eas T1 core", modifier = Modifier.padding(24.dp))
        return
    }
    var exams by remember { mutableStateOf<List<ExamItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(refresh) {
        error = null
        service.exams().observeOnce { state ->
            when (state.state) {
                DataState.STATE.SUCCESS -> exams = state.data.orEmpty()
                else -> error = state.message ?: "读取失败"
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Button(onClick = { refresh++ }) { Text("刷新") }
        error?.let { Text("错误：$it", Modifier.padding(top = 8.dp)) }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(exams, key = { it.memoId }) { exam ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(exam.courseName ?: "未命名", style = MaterialTheme.typography.subtitle1)
                        Text("${exam.examDate.orEmpty()} ${exam.examTime.orEmpty()}", style = MaterialTheme.typography.caption)
                        exam.examLocation?.let { Text(it, style = MaterialTheme.typography.caption) }
                    }
                }
            }
        }
    }
}

private fun <T> LiveData<T>.observeOnce(onChanged: (T) -> Unit) {
    val observer = object : Observer<T> {
        override fun onChanged(value: T) { removeObserver(this); onChanged(value) }
    }
    observeForever(observer)
}
