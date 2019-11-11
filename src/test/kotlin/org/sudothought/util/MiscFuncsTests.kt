/*
 *
 *  Copyright © 2019 Paul Ambrose (pambrose@mac.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package org.sudothought.util

import com.sudothought.common.util.length
import org.amshove.kluent.shouldEqual
import org.junit.jupiter.api.Test

class MiscFuncsTests {

    @Test
    fun lengthTests() {
        (0..10000000).forEach() { i -> i.length shouldEqual i.toString().length }
        (Int.MAX_VALUE - 10000000..Int.MAX_VALUE).forEach() { i -> i.length shouldEqual i.toString().length }

        (0L..10000000L).forEach() { i -> i.length shouldEqual i.toString().length }
        (Long.MAX_VALUE - 10000000L..Long.MAX_VALUE).forEach() { i -> i.length shouldEqual i.toString().length }
    }
}