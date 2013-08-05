/**
 * Copyright (c) 2012, md_5. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * The name of the author may not be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.md_5.specialsource;

import net.md_5.specialsource.util.NoDupeList;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingAnnotationAdapter;

/**
 * A {@link LocalVariablesSorter} for type mapping.
 * 
 * @author Eugene Kuleshov
 * 
 * Edited 04-24-2013 LexManos:
 *  Changed super class to MethodVisitor, using LocalVariablesSorter caused 
 *  the LV indexes to be reassigned improperly. Causing decompiled code to 
 *  not follow a predictable pattern and not coincide with RetroGuard's output.
 */
public class UnsortedRemappingMethodAdapter extends MethodVisitor { //Lex: Changed LocalVariablesSorter to MethodVisitor

    protected final Remapper remapper;
    private final JarMapping mapping;

    public UnsortedRemappingMethodAdapter(final int access, final String desc,
            final MethodVisitor mv, final Remapper remapper, JarMapping mapping) {
        this(Opcodes.ASM4, access, desc, mv, remapper, mapping);
    }

    protected UnsortedRemappingMethodAdapter(final int api, final int access, 
            final String desc, final MethodVisitor mv, final Remapper remapper, JarMapping mapping) {
        super(api, mv); //Lex: Removed access, desc
        this.remapper = remapper;
        this.mapping = mapping;
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
        AnnotationVisitor av = mv.visitAnnotationDefault();
        return av == null ? av : new RemappingAnnotationAdapter(av, remapper);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        AnnotationVisitor av = mv.visitAnnotation(remapper.mapDesc(desc),
                visible);
        return av == null ? av : new RemappingAnnotationAdapter(av, remapper);
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter,
            String desc, boolean visible) {
        AnnotationVisitor av = mv.visitParameterAnnotation(parameter,
                remapper.mapDesc(desc), visible);
        return av == null ? av : new RemappingAnnotationAdapter(av, remapper);
    }

    @Override
    public void visitFrame(int type, int nLocal, Object[] local, int nStack,
            Object[] stack) {
        super.visitFrame(type, nLocal, remapEntries(nLocal, local), nStack,
                remapEntries(nStack, stack));
    }

    private Object[] remapEntries(int n, Object[] entries) {
        for (int i = 0; i < n; i++) {
            if (entries[i] instanceof String) {
                Object[] newEntries = new Object[n];
                if (i > 0) {
                    System.arraycopy(entries, 0, newEntries, 0, i);
                }
                do {
                    Object t = entries[i];
                    newEntries[i++] = t instanceof String ? remapper
                            .mapType((String) t) : t;
                } while (i < n);
                return newEntries;
            }
        }
        return entries;
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name,
            String desc) {
        super.visitFieldInsn(opcode, remapper.mapType(owner),
                remapper.mapFieldName(owner, name, desc,findAccess(owner, name, desc,mapping.oldJar.fields)),
                remapper.mapDesc(desc));
    }
    
    private int findAccess(String owner, String name, String desc, NoDupeList<Ownable> search) {
        Ownable found = null;
        for (Ownable f : search) {
            if (f.owner.equals(owner) && f.name.equals(name) && f.descriptor.equals(desc)) {
                found = f;
                break;
            }
        }
        if (found == null) {
            throw new IllegalStateException(String.format("No reverse lookup for %s %s %s", owner, name, desc));
        }
        
        return found.access;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc) {
        super.visitMethodInsn(opcode, remapper.mapType(owner),
                remapper.mapMethodName(owner, name, desc, findAccess(owner, name, desc, mapping.oldJar.methods)),
                remapper.mapMethodDesc(desc));
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm,
            Object... bsmArgs) {
        for (int i = 0; i < bsmArgs.length; i++) {
            bsmArgs[i] = remapper.mapValue(bsmArgs[i]);
        }
        super.visitInvokeDynamicInsn(
                remapper.mapInvokeDynamicMethodName(name, desc),
                remapper.mapMethodDesc(desc), (Handle) remapper.mapValue(bsm),
                bsmArgs);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        super.visitTypeInsn(opcode, remapper.mapType(type));
    }

    @Override
    public void visitLdcInsn(Object cst) {
        super.visitLdcInsn(remapper.mapValue(cst));
    }

    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) {
        super.visitMultiANewArrayInsn(remapper.mapDesc(desc), dims);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler,
            String type) {
        super.visitTryCatchBlock(start, end, handler, type == null ? null
                : remapper.mapType(type));
    }

    @Override
    public void visitLocalVariable(String name, String desc, String signature,
            Label start, Label end, int index) {
        super.visitLocalVariable(name, remapper.mapDesc(desc),
                remapper.mapSignature(signature, true), start, end, index);
    }
}