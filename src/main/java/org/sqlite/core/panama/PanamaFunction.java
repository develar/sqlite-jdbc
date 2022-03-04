package org.sqlite.core.panama;

import jdk.incubator.foreign.MemoryAddress;
import org.sqlite.Function;
import org.sqlite.core.PanamaDBImpl;

import java.sql.SQLException;


public class PanamaFunction {

    private final Function function;
    private final PanamaDBImpl pDB;

    public PanamaFunction(PanamaDBImpl db, Function call)
    {
        function = call;
        pDB = db;
    }

    public void xFunc(long context, int args, MemoryAddress value) throws SQLException
    {
        synchronized (function) {
            function.setCallback(context, args, value.toRawLongValue());
            try {
                function.xFunc();
            } catch (SQLException ex) {
                pDB.setError(context, ex.getMessage());
            }
        }
        function.setCallback(0, 0, 0);
    }

    public void xStep(long context, int args, MemoryAddress value) throws SQLException
    {
        function.setCallback(context, args, value.toRawLongValue());
        try {
            ((Function.Aggregate)function).xStep();
        }
        catch (SQLException ex)
        {
            pDB.setError(context, ex.getMessage());
        }
        function.setCallback(0, 0, 0);
    }

    public void xInverse(long context, int args, MemoryAddress value) throws SQLException
    {
        function.setCallback(context, args, value.toRawLongValue());
        try {
        ((Function.Window)function).xInverse();
        }
        catch (SQLException ex)
        {
            pDB.setError(context, ex.getMessage());
        }
        function.setCallback(0, 0, 0);
    }

    public void xFinal(long context) throws SQLException
    {
        function.setCallback(context,0, 0);
        try{
            ((Function.Aggregate)function).xFinal();
        }
        catch (SQLException ex)
        {
            pDB.setError(context, ex.getMessage());
        }

        function.setCallback(0, 0, 0);
    }

    public void xValue(long context) throws SQLException
    {
        function.setCallback(context,0, 0);
        try {
        ((Function.Window)function).xValue();
        }
        catch (SQLException ex)
        {
            pDB.setError(context, ex.getMessage());
        }
        function.setCallback(0, 0, 0);
    }

    public MemoryAddress getxFuncCall()
    {
        return getCall("xFunc");
    }

    public MemoryAddress getxStepCall()
    {
        if (!(function instanceof Function.Aggregate))
            throw new IllegalArgumentException("Not an aggregate function!");

        return getCall("xStep");
    }

    public MemoryAddress getxInverseCall()
    {
        if (!(function instanceof Function.Window))
            throw new IllegalArgumentException("Not an aggregate function!");

        return getCall("xInverse");
    }

    public MemoryAddress getxFinalCall()
    {
        if (!(function instanceof Function.Aggregate))
            throw new IllegalArgumentException("Not an aggregate function!");

        return getCall("xFinal");
    }

    public MemoryAddress getxValueCall()
    {
        if (!(function instanceof Function.Aggregate))
            throw new IllegalArgumentException("Not an aggregate function!");
        return getCall("xValue");
    }

    public MemoryAddress getCall(String name)
    {
        return pDB.getCallbackCreator().createCallback(this, name);
    }
}
