package com.stupidtree.hita.ui.eas

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
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
import com.stupidtree.hita.api.EasAuthService
import com.stupidtree.hita.api.EmptyClassroomService
import com.stupidtree.hita.api.ExamService
import com.stupidtree.hita.api.PluginUiHost
import com.stupidtree.hita.api.ScoreReport
import com.stupidtree.hita.api.ScoreService
import com.stupidtree.hita.api.TrainingPlanService
import com.stupidtree.hitax.data.model.eas.ExamItem
import com.stupidtree.hitax.data.model.eas.TermItem

class EasUiPlugin : ComposeUiPlugin {
    override val id = "com.stupidtree.hita.ui.eas"
    override val displayName = "教务"
    override val requiresApi = 7

    override fun createView(context: Context, host: PluginUiHost): View = ComposeView(context).apply {
        setContent {
            MaterialTheme {
                EasScreen(
                    host.service(EasAuthService.KEY),
                    host.service(ScoreService.KEY),
                    host.service(ExamService.KEY),
                    host.service(EmptyClassroomService.KEY),
                    host.service(TrainingPlanService.KEY),
                )
            }
        }
    }
}

@Composable
private fun EasScreen(
    auth: EasAuthService?,
    scores: ScoreService?,
    exams: ExamService?,
    classrooms: EmptyClassroomService?,
    plans: TrainingPlanService?,
) {
    if (auth == null) {
        Text("未安装 eas T1 core", modifier = Modifier.padding(24.dp))
        return
    }
    var logged by remember { mutableStateOf(auth.isLogin()) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var terms by remember { mutableStateOf<List<TermItem>>(emptyList()) }
    var report by remember { mutableStateOf<ScoreReport?>(null) }
    var examItems by remember { mutableStateOf<List<ExamItem>>(emptyList()) }
    var buildings by remember { mutableStateOf<List<String>>(emptyList()) }
    var rooms by remember { mutableStateOf<List<String>>(emptyList()) }
    var planNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var section by remember { mutableStateOf("scores") }

    LaunchedEffect(logged) {
        if (!logged) return@LaunchedEffect
        scores?.terms()?.observeOnce { state ->
            if (state.state == DataState.STATE.SUCCESS) {
                terms = state.data.orEmpty()
                terms.firstOrNull()?.let { term ->
                    scores.report(term).observeOnce { r ->
                        if (r.state == DataState.STATE.SUCCESS) report = r.data
                        else message = r.message
                    }
                }
            } else message = state.message
        }
        exams?.exams()?.observeOnce { state ->
            if (state.state == DataState.STATE.SUCCESS) examItems = state.data.orEmpty() else message = state.message
        }
        classrooms?.buildings()?.observeOnce { state ->
            if (state.state == DataState.STATE.SUCCESS) {
                val list = state.data.orEmpty()
                buildings = list.map { it.name }
                list.firstOrNull()?.let { b ->
                    classrooms.query(b.id, 1).observeOnce { q ->
                        if (q.state == DataState.STATE.SUCCESS) rooms = q.data.orEmpty().map { it.name } else message = q.message
                    }
                }
            } else message = state.message
        }
        plans?.plans()?.observeOnce { state ->
            if (state.state == DataState.STATE.SUCCESS) planNames = state.data.orEmpty().map { it.name } else message = state.message
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        if (!logged) {
            Text("教务登录", style = MaterialTheme.typography.h6)
            OutlinedTextField(username, { username = it }, label = { Text("学号") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(password, { password = it }, label = { Text("密码") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Button(
                onClick = {
                    auth.login(username.trim(), password).observeOnce { state ->
                        if (state.state == DataState.STATE.SUCCESS && state.data == true) {
                            logged = true; message = null
                        } else message = state.message ?: "登录失败"
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("登录") }
            message?.let { Text("错误：$it") }
            return@Column
        }
        Row(Modifier.fillMaxWidth()) {
            Text(auth.accountName() ?: "已登录", Modifier.weight(1f))
            Button(onClick = { auth.logout(); logged = false; report = null; examItems = emptyList() }) { Text("登出") }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            listOf("scores" to "成绩", "exams" to "考试", "rooms" to "空教室", "plan" to "培养计划").forEach { (id, title) ->
                Button(onClick = { section = id }, modifier = Modifier.padding(end = 4.dp)) { Text(title) }
            }
        }
        message?.let { Text("错误：$it") }
        when (section) {
            "scores" -> {
                Text("学期：${terms.size}", style = MaterialTheme.typography.caption)
                LazyColumn {
                    items(report?.entries.orEmpty()) { entry ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Row(Modifier.padding(10.dp)) {
                                Text(entry.courseName, Modifier.weight(1f))
                                Text(entry.score.toString())
                            }
                        }
                    }
                }
            }
            "exams" -> LazyColumn {
                items(examItems, key = { it.memoId }) { e ->
                    Text("${e.courseName ?: "-"}  ${e.examDate.orEmpty()} ${e.examTime.orEmpty()}", Modifier.padding(4.dp))
                }
            }
            "rooms" -> Column {
                Text("教学楼：${buildings.joinToString("、")}", style = MaterialTheme.typography.caption)
                LazyColumn { items(rooms) { Text(it, Modifier.padding(4.dp)) } }
            }
            "plan" -> LazyColumn { items(planNames) { Text(it, Modifier.padding(4.dp)) } }
        }
    }
}

private fun <T> LiveData<T>.observeOnce(onChanged: (T) -> Unit) {
    val observer = object : Observer<T> {
        override fun onChanged(value: T) { removeObserver(this); onChanged(value) }
    }
    observeForever(observer)
}
