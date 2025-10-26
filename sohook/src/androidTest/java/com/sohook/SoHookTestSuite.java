package com.sohook;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * SoHook 测试套件
 * 包含所有测试类
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    SoHookBasicTest.class,
    SoHookMemoryLeakTest.class,
    SoHookStressTest.class,
    SoHookAccuracyTest.class,
    SoHookCppTest.class,
    SoHookFdLeakTest.class,
})
public class SoHookTestSuite {
    // 测试套件类，用于一次运行所有测试
}
