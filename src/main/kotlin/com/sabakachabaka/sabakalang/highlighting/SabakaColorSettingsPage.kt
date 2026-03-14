package com.sabakachabaka.sabakalang.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.sabakachabaka.sabakalang.SabakaFileType

class SabakaColorSettingsPage : ColorSettingsPage {

    override fun getIcon() = SabakaFileType.INSTANCE.icon
    override fun getHighlighter(): SyntaxHighlighter = SabakaSyntaxHighlighter()
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getDisplayName() = "SabakaLang"

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = arrayOf(
        AttributesDescriptor("Keywords//Control flow",         SabakaColors.KEYWORD),
        AttributesDescriptor("Keywords//Types",                SabakaColors.TYPE_KEYWORD),
        AttributesDescriptor("Literals//Number",               SabakaColors.NUMBER),
        AttributesDescriptor("Literals//String",               SabakaColors.STRING),
        AttributesDescriptor("Literals//Boolean",              SabakaColors.BOOL_LIT),
        AttributesDescriptor("Comment",                        SabakaColors.COMMENT),
        AttributesDescriptor("Operator",                       SabakaColors.OPERATOR),
        AttributesDescriptor("Braces//Parentheses",            SabakaColors.PAREN),
        AttributesDescriptor("Braces//Braces",                 SabakaColors.BRACE),
        AttributesDescriptor("Braces//Brackets",               SabakaColors.BRACKET),
        AttributesDescriptor("Braces//Semicolon",              SabakaColors.SEMICOLON),
        AttributesDescriptor("Braces//Comma",                  SabakaColors.COMMA),
        AttributesDescriptor("Braces//Dot",                    SabakaColors.DOT),
        AttributesDescriptor("Identifiers//Default",           SabakaColors.IDENTIFIER),
        AttributesDescriptor("Identifiers//Function decl",     SabakaColors.FUNC_DECL),
        AttributesDescriptor("Identifiers//Function call",     SabakaColors.FUNC_CALL),
        AttributesDescriptor("Identifiers//Built-in function", SabakaColors.BUILTIN_CALL),
        AttributesDescriptor("Identifiers//Class name",        SabakaColors.CLASS_NAME),
        AttributesDescriptor("Identifiers//Struct name",       SabakaColors.STRUCT_NAME),
        AttributesDescriptor("Identifiers//Enum name",         SabakaColors.ENUM_NAME),
        AttributesDescriptor("Identifiers//Parameter",         SabakaColors.PARAM),
        AttributesDescriptor("Identifiers//Local variable",    SabakaColors.LOCAL_VAR),
        AttributesDescriptor("Bad character",                  SabakaColors.BAD_CHAR),
    )

    // Demo uses real SabakaLang syntax: no `func` keyword, type comes first
    override fun getDemoText() = """
// SabakaLang — correct syntax demo
import "math.sabaka";

enum Direction { North, South, East, West }

struct Point {
    int x;
    int y;
}

interface IShape {
    float area();
    void draw();
}

class Animal {
    private string name;
    public int age;

    public void Animal(string n, int a) {
        name = n;
        age = a;
    }

    public void speak() {
        print("I am " + name);
    }
}

class Dog : Animal {
    override void speak() {
        print("Woof! I am " + name);
    }
}

class List<T> {
    T[] values = [];

    void push_back(T value) {
        T[] newValues = [];
        for (int i = 0; i < values.length; i = i + 1) {
            newValues[i] = values[i];
        }
        newValues[values.length] = value;
        values = newValues;
    }

    T getValue(int index) {
        return values[index];
    }

    void List() {}
}

int factorial(int n) {
    if (n <= 1) return 1;
    return n * factorial(n - 1);
}

void main() {
    int x = 42;
    float pi = 3.14;
    string msg = "Hello, SabakaLang!";
    bool flag = true;

    print(msg);
    string line = input();
    sleep(100);

    string content = readFile("data.txt");
    writeFile("out.txt", "hello");
    bool exists = fileExists("test.txt");
    string resp = httpGet("https://api.example.com");
    int t = timeMs();
    int code = ord("A");
    string ch = chr(65);

    int[] nums = [1, 2, 3, 4, 5];
    int len = nums.length;

    foreach (int n in nums) {
        print(n);
    }

    for (int i = 0; i < 10; i = i + 1) {
        if (i % 2 == 0) {
            print("even: " + i);
        } else {
            print("odd: " + i);
        }
    }

    switch (x) {
        case 1: print("one");
        case 42: print("the answer");
        default: print("other");
    }

    Dog d = new Dog("Rex", 3);
    d.speak();
    int result = factorial(10);
    print(result);
}
""".trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = emptyMap()
}
