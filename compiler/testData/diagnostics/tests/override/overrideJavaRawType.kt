// FIR_IDENTICAL
// ISSUE: KT-56626

// FILE: JavaBreakpointType.java

import org.jetbrains.annotations.NotNull;

public interface JavaBreakpointType<P> {
    @NotNull
    Breakpoint<P> createJavaBreakpoint(Breakpoint<P> breakpoint);
}

// FILE: Breakpoint.java

public abstract class Breakpoint<P> {}

// FILE: JavaLineBreakpointTypeBase.java

public abstract class JavaLineBreakpointTypeBase<P> implements JavaBreakpointType<P> {}

// FILE: JavaMethodBreakpointType.java

import org.jetbrains.annotations.NotNull;

public class JavaMethodBreakpointType extends JavaLineBreakpointTypeBase<String> {
    @NotNull
    @Override
    public Breakpoint<String> createJavaBreakpoint(Breakpoint breakpoint) {
        return null;
    }
}

// FILE: MethodBreakpoint.java

public class MethodBreakpoint extends Breakpoint<String> {}

// FILE: KotlinFunctionBreakpointType.kt

class KotlinFunctionBreakpointType : JavaMethodBreakpointType() {
    override fun createJavaBreakpoint(breakpoint: Breakpoint<String>) = MethodBreakpoint()
}
