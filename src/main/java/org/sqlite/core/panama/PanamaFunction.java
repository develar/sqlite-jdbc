package org.sqlite.core.panama;

import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.ResourceScope;
import org.sqlite.Function;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.sql.SQLException;

import static jdk.incubator.foreign.CLinker.*;

public class PanamaFunction {
    private Function function;

    public PanamaFunction(Function call)
    {
        function = call;
    }

    public void xFunc(long context, int args, MemoryAddress value) throws SQLException
    {
        function.setCallback(context, args, value.toRawLongValue());
        function.xFunc();
        function.setCallback(0, 0, 0);
    }

    public void xStep(long context, int args, MemoryAddress value) throws SQLException
    {
        function.setCallback(context, args, value.toRawLongValue());
        ((Function.Aggregate)function).xStep();
        function.setCallback(0, 0, 0);
    }

    public void xInverse(long context, int args, MemoryAddress value) throws SQLException
    {
        function.setCallback(context, args, value.toRawLongValue());
        ((Function.Window)function).xInverse();
        function.setCallback(0, 0, 0);
    }

    public void xFinal(long context) throws SQLException
    {
        function.setCallback(context,0, 0);
        ((Function.Aggregate)function).xFinal();
        function.setCallback(0, 0, 0);
    }

    public void xValue(long context) throws SQLException
    {
        function.setCallback(context,0, 0);
        ((Function.Window)function).xValue();
        function.setCallback(0, 0, 0);
    }

    public MemoryAddress getxFuncCall()
    {
        return getCall3("xFunc");
    }

    public MemoryAddress getxStepCall()
    {
        if (!(function instanceof Function.Aggregate))
            throw new IllegalArgumentException("Not an aggregate function!");

        return getCall3("xStep");
    }

    public MemoryAddress getxInverseCall()
    {
        if (!(function instanceof Function.Window))
            throw new IllegalArgumentException("Not an aggregate function!");

        return getCall3("xInverse");
    }



    public MemoryAddress getxFinalCall()
    {
        if (!(function instanceof Function.Aggregate))
            throw new IllegalArgumentException("Not an aggregate function!");

        return getCall1("xFinal");
    }

    public MemoryAddress getxValueCall()
    {
        if (!(function instanceof Function.Aggregate))
            throw new IllegalArgumentException("Not an aggregate function!");
        return getCall1("xValue");
    }

    public MemoryAddress getCall1(String name)
    {
        if (!(function instanceof Function.Aggregate))
            throw new IllegalArgumentException("Not an aggregate function!");

        try {
            var lookup = MethodHandles.lookup();
            var mtype = MethodType.methodType(void.class, long.class);
            var handle = lookup.findVirtual(PanamaFunction.class, name, mtype);
            var handleToCall = handle.bindTo(this);

            ResourceScope scope = ResourceScope.globalScope();
            return CLinker.getInstance().upcallStub(handleToCall, FunctionDescriptor.ofVoid(C_LONG_LONG), scope);
        }
        catch (NoSuchMethodException | IllegalAccessException ex)
        {
            ex.printStackTrace();
        }

        return MemoryAddress.NULL;
    }

    private MemoryAddress getCall3(String name)
    {
        try {
            var lookup = MethodHandles.lookup();
            var mtype = MethodType.methodType(void.class, long.class, int.class, MemoryAddress.class);
            var handle = lookup.findVirtual(PanamaFunction.class, name, mtype);
            var handleToCall = handle.bindTo(this);

            ResourceScope scope = ResourceScope.globalScope();
            return CLinker.getInstance().upcallStub(handleToCall, FunctionDescriptor.ofVoid(C_LONG_LONG, C_INT, C_POINTER), scope);
        }
        catch (NoSuchMethodException | IllegalAccessException ex)
        {
            ex.printStackTrace();
        }

        return MemoryAddress.NULL;
    }
}
