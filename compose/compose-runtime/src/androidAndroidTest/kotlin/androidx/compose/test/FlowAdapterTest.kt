/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.test

import androidx.compose.collectAsState
import androidx.compose.getValue
import androidx.compose.mutableStateOf
import androidx.compose.setValue
import androidx.test.filters.MediumTest
import androidx.ui.test.createComposeRule
import androidx.ui.test.runOnIdle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

@MediumTest
@RunWith(JUnit4::class)
@ExperimentalCoroutinesApi
@FlowPreview
class FlowAdapterTest {

    private class FlowChannel<T> {
        val channel = BroadcastChannel<T>(1)
        val flow = channel.asFlow()
    }

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun weReceiveSubmittedValue() {
        val stream = FlowChannel<String>()

        var realValue: String? = null
        rule.setContent {
            realValue = stream.flow.collectAsState(initial = null).value
        }

        runOnIdle {
            stream.channel.offer("value")
        }

        runOnIdle {
            assertThat(realValue).isEqualTo("value")
        }
    }

    @Test
    fun weReceiveSecondValue() {
        val stream = FlowChannel<String>()

        var realValue: String? = null
        rule.setContent {
            realValue = stream.flow.collectAsState(initial = null).value
        }

        runOnIdle {
            stream.channel.offer("value")
        }

        runOnIdle {
            assertThat(realValue).isEqualTo("value")
            stream.channel.offer("value2")
        }

        runOnIdle {
            assertThat(realValue).isEqualTo("value2")
        }
    }

    @Test
    fun noUpdatesAfterDispose() {
        val stream = FlowChannel<String>()
        var emit by mutableStateOf(true)
        var realValue: String? = "to-be-updated"
        rule.setContent {
            if (emit) {
                realValue = stream.flow.collectAsState(initial = null).value
            }
        }

        runOnIdle { emit = false }

        runOnIdle {
            stream.channel.offer("value")
        }

        runOnIdle {
            assertThat(realValue).isNull()
        }
    }

    @Test
    fun testCollectionWithInitialValue() {
        val stream = FlowChannel<String>()
        var realValue = "to-be-updated"
        rule.setContent {
            realValue = stream.flow.collectAsState("value").value
        }

        assertThat(realValue).isEqualTo("value")
    }

    @Test
    fun testOverridingInitialValue() {
        val stream = FlowChannel<String>()
        var realValue = "to-be-updated"
        rule.setContent {
            realValue = stream.flow.collectAsState("value").value
        }

        runOnIdle {
            stream.channel.offer("value2")
        }

        runOnIdle {
            assertThat(realValue).isEqualTo("value2")
        }
    }

    @Test
    fun theCurrentValueIsNotLostWhenWeUpdatedInitial() {
        val stream = FlowChannel<String>()
        var initial by mutableStateOf("initial1")

        var realValue: String? = null
        rule.setContent {
            realValue = stream.flow.collectAsState(initial).value
        }

        runOnIdle {
            initial = "initial2"
        }

        runOnIdle {
            assertThat(realValue).isEqualTo("initial1")
        }
    }

    @Test
    fun replacingStreams() {
        val stream1 = FlowChannel<String>()
        val stream2 = FlowChannel<String>()
        var stream by mutableStateOf(stream1)

        var realValue: String? = null
        rule.setContent {
            realValue = stream.flow.collectAsState(initial = null).value
        }

        runOnIdle {
            stream = stream2
        }

        runOnIdle {
            stream2.channel.offer("stream2")
        }

        runOnIdle {
            stream1.channel.offer("stream1")
        }

        runOnIdle {
            assertThat(realValue).isEqualTo("stream2")
        }
    }

    @Test
    fun theCurrentValueIsNotLostWhenWeReplacedStreams() {
        val stream1 = FlowChannel<String>()
        val stream2 = FlowChannel<String>()
        var stream by mutableStateOf(stream1)

        var realValue: String? = null
        rule.setContent {
            realValue = stream.flow.collectAsState(initial = null).value
        }

        runOnIdle {
            stream1.channel.offer("value")
        }

        runOnIdle {
            stream = stream2
        }

        runOnIdle {
            assertThat(realValue).isEqualTo("value")
        }
    }

    @Test
    fun observingOnCustomContext() {
        val stream = FlowChannel<String>()
        val latch = CountDownLatch(1)

        var realValue: String? = null
        rule.setContent {
            realValue = stream.flow.collectAsState(null, Dispatchers.Default).value
            if (realValue != null) {
                latch.countDown()
            }
        }

        runOnIdle {
            stream.channel.offer("value")
        }

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()

        runOnIdle {
            assertThat(realValue).isEqualTo("value")
        }
    }

    @Test
    fun theCurrentValueIsNotLostWhenWeReplacedContext() {
        val stream = FlowChannel<String>()
        var context by mutableStateOf<CoroutineContext>(Dispatchers.Main)

        var realValue: String? = null
        rule.setContent {
            realValue = stream.flow.collectAsState(null, context).value
        }

        runOnIdle {
            stream.channel.offer("value")
        }

        runOnIdle {
            context = Dispatchers.IO
        }

        runOnIdle {
            assertThat(realValue).isEqualTo("value")
        }
    }

    @Test
    fun testInitialValueOfStateFlow() {
        val flow = MutableStateFlow("initial")
        var realValue = "to-be-updated"
        rule.setContent {
            realValue = flow.collectAsState().value
        }

        runOnIdle {
            assertThat(realValue).isEqualTo("initial")
        }
    }

    @Test
    fun stateFlowHandlesNullValue() {
        val flow = MutableStateFlow<String?>(null)
        var realValue: String? = "to-be-updated"
        rule.setContent {
            realValue = flow.collectAsState().value
        }

        runOnIdle {
            assertThat(realValue).isNull()
        }
    }

    @Test
    fun updatingValueOfStateFlow() {
        val flow = MutableStateFlow("initial")
        var realValue = "to-be-updated"
        rule.setContent {
            realValue = flow.collectAsState().value
        }

        runOnIdle {
            flow.value = "updated"
        }

        runOnIdle {
            assertThat(realValue).isEqualTo("updated")
        }
    }
}
