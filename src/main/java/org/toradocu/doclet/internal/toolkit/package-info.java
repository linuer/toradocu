/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
    Contains the base classes that make up a doclet.  Doclets that reuse
    the functionality provided by the toolkit should have the following
    characteristics:
    <ul>
        <li>
            The main driver class should extend
            {@link org.toradocu.doclet.internal.toolkit.AbstractDoclet}.
        </li>
        <li>
            The doclet configuration class should extend
            {@link org.toradocu.doclet.internal.toolkit.Configuration}.
        </li>
        <li>
            The doclet should have a writer factory that implements
            {@link org.toradocu.doclet.internal.toolkit.WriterFactory}.
            This class constructs writers that write doclet specific output.
        </li>
        <li>
            The doclet should have a taglet writer that extends
            {@link org.toradocu.doclet.internal.toolkit.taglets.TagletWriter}.
             This writer determines how to output each given tag.
        </li>
    </ul>

    <p><b>This is NOT part of any supported API.
    If you write code that depends on this, you do so at your own risk.
    This code and its internal interfaces are subject to change or
    deletion without notice.</b>
*/
@jdk.Exported(false)
package org.toradocu.doclet.internal.toolkit;