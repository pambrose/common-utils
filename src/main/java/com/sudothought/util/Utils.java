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

package com.sudothought.util;

public class Utils {
    private static final java.util.Random random = new java.util.Random();

    public static long random(long upper) {
        return Math.abs(random.nextLong() % upper);
    }

    public static int random(int upper) {
        return Math.abs(random.nextInt() % upper);
    }

    public static void sleepMillis(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void sleepSecs(long time) {
        sleepMillis(time * 1000);
    }
}