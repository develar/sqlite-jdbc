package org.sqlite.core.panama;

import jdk.incubator.foreign.*;
import org.sqlite.core.PanamaDBImpl;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PanamaCallbacks {

    private final ResourceScope callbackScope = ResourceScope.newImplicitScope();

    public MemoryAddress createCallback(Object ob, String methodName)
    {
        var methods = getDeclaredMethods(ob.getClass());

        methods = methods.stream().filter(m -> m.getName().equals(methodName)).collect(Collectors.toList());
        if (methods.size() == 0)
            throw new IllegalArgumentException("Could not find method " + methodName + " in class " + ob.getClass().getName());
        else if (methods.size() > 1)
            throw new IllegalArgumentException("Multiple overloads of method " + methodName + " in class " + ob.getClass().getName());

        Method callbackMethod = methods.get(0);

        Class<?> retType = callbackMethod.getReturnType();
        Class<?>[] parameters = callbackMethod.getParameterTypes();

        if (!retType.isPrimitive() && retType != MemoryAddress.class)
            throw new IllegalArgumentException("Callback method must return void, primitives, or MemoryAddress, not " + retType.getName());


        for (Class<?> parameter : parameters) {
            if (!parameter.isPrimitive() && parameter != MemoryAddress.class)
                throw new IllegalArgumentException("Callback parameters must be primitives or MemoryAddress, not " + parameter.getName());
        }

        MemoryLayout[] memoryLayout = Arrays.stream(parameters).map(PanamaCallbacks::classToMemory).toArray(MemoryLayout[]::new);
        FunctionDescriptor fd;
        if (void.class.equals(retType))
            fd = FunctionDescriptor.ofVoid(memoryLayout);
        else
            fd = FunctionDescriptor.of(classToMemory(retType), memoryLayout);

        try {
            var handle = MethodHandles.lookup().findVirtual(ob.getClass(), methodName, MethodType.methodType(retType, parameters));
            var handleToCall = handle.bindTo(ob);

            return CLinker.getInstance().upcallStub(handleToCall, fd, callbackScope);
        }
        catch (NoSuchMethodException | IllegalAccessException ex)
        {
            throw new Error("Failed to create callback method", ex);
        }
    }

    static List<Method> getDeclaredMethods(Class<?> interfaceClass) {
        Method[] methods = interfaceClass.getDeclaredMethods();
        return Arrays.stream(methods).filter(method -> (method.getModifiers() & Modifier.STATIC) == 0).toList();
    }

    private static MemoryLayout classToMemory(Class<?> type)
    {
        if (double.class.equals(type))
            return CLinker.C_DOUBLE;
        if (int.class.equals(type))
            return CLinker.C_INT;
        if (float.class.equals(type))
            return CLinker.C_FLOAT;
        if (short.class.equals(type))
            return CLinker.C_SHORT;
        if (byte.class.equals(type))
            return CLinker.C_CHAR;
        if (long.class.equals(type))
            return CLinker.C_LONG_LONG;

        return CLinker.C_POINTER;
    }
}
