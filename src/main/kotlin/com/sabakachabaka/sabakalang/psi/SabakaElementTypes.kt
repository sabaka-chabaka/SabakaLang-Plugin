package com.sabakachabaka.sabakalang.psi

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.sabakachabaka.sabakalang.SabakaLanguage

class SabakaElementType(debugName: String) : IElementType(debugName, SabakaLanguage) {
    override fun toString() = "SabakaElementType.$debugName"
}

object SabakaFileElementType : IFileElementType("SABAKA_FILE", SabakaLanguage)

/**
 * All composite PSI node types for SabakaLang.
 *
 * Function syntax reminder:
 *   ReturnType name(ParamType paramName, ...) { body }
 *   e.g.  void main() { }
 *         int add(int a, int b) { return a + b; }
 *         T getValue(int index) { return values[index]; }
 */
object SabakaElementTypes {
    @JvmField val FILE              = SabakaFileElementType

    // Top-level
    @JvmField val IMPORT_STMT       = SabakaElementType("IMPORT_STMT")
    @JvmField val FUNC_DECL         = SabakaElementType("FUNC_DECL")   // ReturnType name(...) { }
    @JvmField val STRUCT_DECL       = SabakaElementType("STRUCT_DECL")
    @JvmField val ENUM_DECL         = SabakaElementType("ENUM_DECL")
    @JvmField val CLASS_DECL        = SabakaElementType("CLASS_DECL")
    @JvmField val INTERFACE_DECL    = SabakaElementType("INTERFACE_DECL")

    // Class internals
    @JvmField val CLASS_BODY        = SabakaElementType("CLASS_BODY")
    @JvmField val METHOD_DECL       = SabakaElementType("METHOD_DECL")  // inside class
    @JvmField val FIELD_DECL        = SabakaElementType("FIELD_DECL")

    // Parameters
    @JvmField val PARAM_LIST        = SabakaElementType("PARAM_LIST")
    @JvmField val PARAM             = SabakaElementType("PARAM")

    // Statements
    @JvmField val BLOCK             = SabakaElementType("BLOCK")
    @JvmField val VAR_DECL_STMT     = SabakaElementType("VAR_DECL_STMT")
    @JvmField val ASSIGN_STMT       = SabakaElementType("ASSIGN_STMT")
    @JvmField val RETURN_STMT       = SabakaElementType("RETURN_STMT")
    @JvmField val IF_STMT           = SabakaElementType("IF_STMT")
    @JvmField val WHILE_STMT        = SabakaElementType("WHILE_STMT")
    @JvmField val FOR_STMT          = SabakaElementType("FOR_STMT")
    @JvmField val FOREACH_STMT      = SabakaElementType("FOREACH_STMT")
    @JvmField val SWITCH_STMT       = SabakaElementType("SWITCH_STMT")
    @JvmField val CASE_CLAUSE       = SabakaElementType("CASE_CLAUSE")
    @JvmField val EXPR_STMT         = SabakaElementType("EXPR_STMT")

    // Expressions
    @JvmField val BINARY_EXPR       = SabakaElementType("BINARY_EXPR")
    @JvmField val UNARY_EXPR        = SabakaElementType("UNARY_EXPR")
    @JvmField val CALL_EXPR         = SabakaElementType("CALL_EXPR")
    @JvmField val MEMBER_ACCESS_EXPR = SabakaElementType("MEMBER_ACCESS_EXPR")
    @JvmField val ARRAY_ACCESS_EXPR = SabakaElementType("ARRAY_ACCESS_EXPR")
    @JvmField val ARRAY_LITERAL     = SabakaElementType("ARRAY_LITERAL")
    @JvmField val NEW_EXPR          = SabakaElementType("NEW_EXPR")
    @JvmField val LITERAL_EXPR      = SabakaElementType("LITERAL_EXPR")
    @JvmField val VAR_EXPR          = SabakaElementType("VAR_EXPR")
    @JvmField val SUPER_EXPR        = SabakaElementType("SUPER_EXPR")
    @JvmField val ARG_LIST          = SabakaElementType("ARG_LIST")

    // Types
    @JvmField val TYPE_REF          = SabakaElementType("TYPE_REF")

    // Struct / enum / interface bodies
    @JvmField val STRUCT_BODY       = SabakaElementType("STRUCT_BODY")
    @JvmField val ENUM_BODY         = SabakaElementType("ENUM_BODY")
    @JvmField val ENUM_MEMBER       = SabakaElementType("ENUM_MEMBER")
    @JvmField val INTERFACE_BODY    = SabakaElementType("INTERFACE_BODY")
    @JvmField val INTERFACE_METHOD  = SabakaElementType("INTERFACE_METHOD")
}
