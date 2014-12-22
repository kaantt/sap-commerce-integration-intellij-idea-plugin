package com.intellij.idea.plugin.hybris.impex;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.idea.plugin.hybris.impex.psi.ImpexTypes;
import com.intellij.psi.TokenType;
import com.intellij.psi.CustomHighlighterTokenType;

%%

%class ImpexLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{
    return;
%eof}
%ignorecase

identifier  = [a-zA-Z0-9_-]

crlf        = (([\n])|([\r])|(\r\n))
not_crlf    = [^\r\n]
white_space = [ \t\f]

end_of_line_comment_marker = [#]
end_of_line_comment_body   = {not_crlf}*

bean_shell_marker = [#][%]
bean_shell_body = (({double_string})|{not_crlf}*)

single_string = ['](('')|([^'\r\n])*)[']
// Double string can contain line break
double_string = [\"](([\"][\"])|[^\"])*[\"]

macro_declaration = [$]({identifier})+{white_space}*[=]
macro_usage       = [$]({identifier})+
macro_value       = ({not_crlf}|{identifier}+)

left_square_bracket  = [\[]
right_square_bracket = [\]]

left_round_bracket  = [\(]
right_round_bracket = [\)]

semicolon    = [;]
comma        = [,]
assign_value = [=]

default_path_delimiter      = [:]
default_key_value_delimiter = "->"
alternative_map_delimiter   = [|]

boolean = (("true")|("false"))
digit   = [[:digit:]]+
class_with_package = ({identifier}+[.]{identifier}+)+

parameter_name = ({identifier}+[.]?{identifier}+)+
alternative_pattern = [|]
special_parameter_name = [@]{identifier}+

attribute_name  = ({identifier}|[.])+
attribute_value = [^, \t\f\]\r\n]+

document_id = [&]{identifier}+

header_mode_insert        = "INSERT"
header_mode_update        = "UPDATE"
header_mode_insert_update = "INSERT_UPDATE"
header_mode_remove        = "REMOVE"

header_type = {identifier}+

value_subtype      = {identifier}+
field_value        = ({not_crlf}|{identifier}+)
field_value_ignore = "<ignore>"

%state COMMENT
%state WAITING_MACRO_VALUE
%state MACRO_DECLARATION
%state HEADER_TYPE
%state HEADER_LINE
%state FIELD_VALUE
%state BEAN_SHELL
%state MODYFIERS_BLOCK
%state WAITING_ATTR_OR_PARAM_VALUE
%state HEADER_PARAMETERS
%state MACRO_USAGE

%%

{crlf}                                                      { yybegin(YYINITIAL); return ImpexTypes.CRLF; }

{white_space}+                                              { return TokenType.WHITE_SPACE; }

<YYINITIAL> {
    {bean_shell_marker}                                     { yybegin(BEAN_SHELL); return ImpexTypes.BEAN_SHELL_MARKER; }
    {double_string}                                         { return ImpexTypes.DOUBLE_STRING; }

    {end_of_line_comment_marker}                            { yybegin(COMMENT); return ImpexTypes.COMMENT_MARKER; }

    {macro_usage}                                           { yybegin(MACRO_USAGE); return ImpexTypes.MACRO_USAGE; }
    {macro_declaration}                                     { yybegin(MACRO_DECLARATION); /* Push back '='. */ yypushback(1); return ImpexTypes.MACRO_DECLARATION; }

    {header_mode_insert}                                    { yybegin(HEADER_TYPE); return ImpexTypes.HEADER_MODE_INSERT; }
    {header_mode_update}                                    { yybegin(HEADER_TYPE); return ImpexTypes.HEADER_MODE_UPDATE; }
    {header_mode_insert_update}                             { yybegin(HEADER_TYPE); return ImpexTypes.HEADER_MODE_INSERT_UPDATE; }
    {header_mode_remove}                                    { yybegin(HEADER_TYPE); return ImpexTypes.HEADER_MODE_REMOVE; }

    {value_subtype}                                         { yybegin(FIELD_VALUE); return ImpexTypes.VALUE_SUBTYPE; }
    {semicolon}                                             { yybegin(FIELD_VALUE); return ImpexTypes.FIELD_VALUE_SEPARATOR; }
}

<MACRO_USAGE> {
    //$START_USERRIGHTS;;;;;;;;;
    //$END_USERRIGHTS;;;;;
    {semicolon}                                             { return ImpexTypes.SEMICOLON; }
}

<COMMENT> {
    {end_of_line_comment_body}                              { return ImpexTypes.COMMENT_BODY; }
}

<BEAN_SHELL> {
    {bean_shell_body}                                       { return ImpexTypes.BEAN_SHELL_BODY; }
}

<FIELD_VALUE> {
    {semicolon}                                             { return ImpexTypes.FIELD_VALUE_SEPARATOR; }
    {double_string}                                         { return ImpexTypes.DOUBLE_STRING; }
    {field_value_ignore}                                    { return ImpexTypes.FIELD_VALUE_IGNORE; }
    {boolean}                                               { return ImpexTypes.BOOLEAN; }
    {digit}                                                 { return ImpexTypes.DIGIT; }
    {class_with_package}                                    { return ImpexTypes.CLASS_WITH_PACKAGE; }

    {comma}                                                 { return ImpexTypes.FIELD_LIST_ITEM_SEPARATOR; }
    {default_path_delimiter}                                { return ImpexTypes.DEFAULT_PATH_DELIMITER; }
    {alternative_map_delimiter}                             { return ImpexTypes.ALTERNATIVE_MAP_DELIMITER; }
    {default_key_value_delimiter}                           { return ImpexTypes.DEFAULT_KEY_VALUE_DELIMITER; }

    {macro_usage}                                           { return ImpexTypes.MACRO_USAGE; }

    {field_value}                                           { return ImpexTypes.FIELD_VALUE; }
}

<HEADER_TYPE> {
    {header_type}                                           { yybegin(HEADER_LINE); return ImpexTypes.HEADER_TYPE; }
}

<HEADER_LINE> {
    {semicolon}                                             { return ImpexTypes.PARAMETERS_SEPARATOR; }
    {comma}                                                 { return ImpexTypes.COMMA; }

    {macro_usage}                                           { return ImpexTypes.MACRO_USAGE; }
    {document_id}                                           { return ImpexTypes.DOCUMENT_ID; }
    {parameter_name}                                        { return ImpexTypes.HEADER_PARAMETER_NAME; }
    {alternative_pattern}                                   { return ImpexTypes.ALTERNATIVE_PATTERN; }
    {special_parameter_name}                                { return ImpexTypes.HEADER_SPECIAL_PARAMETER_NAME; }
    {assign_value}                                          { yybegin(WAITING_ATTR_OR_PARAM_VALUE); return ImpexTypes.ASSIGN_VALUE; }

    {left_round_bracket}                                    { return ImpexTypes.ROUND_BRACKETS; }
    {right_round_bracket}                                   { return ImpexTypes.ROUND_BRACKETS; }

    {left_square_bracket}                                   { yybegin(MODYFIERS_BLOCK); return ImpexTypes.SQUARE_BRACKETS; }
    {right_square_bracket}                                  { return ImpexTypes.SQUARE_BRACKETS; }
}

<MODYFIERS_BLOCK> {
    {attribute_name}                                        { return ImpexTypes.ATTRIBUTE_NAME; }

    {assign_value}                                          { yybegin(WAITING_ATTR_OR_PARAM_VALUE); return ImpexTypes.ASSIGN_VALUE; }

    {single_string}                                         { return ImpexTypes.SINGLE_STRING; }
    {double_string}                                         { return ImpexTypes.DOUBLE_STRING; }

    {right_square_bracket}                                  { yybegin(HEADER_LINE); return ImpexTypes.SQUARE_BRACKETS; }

    {comma}                                                 { return ImpexTypes.ATTRIBUTE_SEPARATOR; }

    {alternative_map_delimiter}                             { yybegin(MODYFIERS_BLOCK); return ImpexTypes.ALTERNATIVE_MAP_DELIMITER; }
}

<WAITING_ATTR_OR_PARAM_VALUE> {
    {boolean}                                               { return ImpexTypes.BOOLEAN; }
    {digit}                                                 { return ImpexTypes.DIGIT; }
    {single_string}                                         { return ImpexTypes.SINGLE_STRING; }
    {double_string}                                         { return ImpexTypes.DOUBLE_STRING; }
    {class_with_package}                                    { return ImpexTypes.CLASS_WITH_PACKAGE; }
    {macro_usage}                                           { return ImpexTypes.MACRO_USAGE; }
    {comma}                                                 { yybegin(MODYFIERS_BLOCK); return ImpexTypes.ATTRIBUTE_SEPARATOR; }
    {attribute_value}                                       { return ImpexTypes.ATTRIBUTE_VALUE; }
    {right_square_bracket}                                  { yybegin(HEADER_LINE); return ImpexTypes.SQUARE_BRACKETS; }
}

<MACRO_DECLARATION> {
    {assign_value}                                          { yybegin(WAITING_MACRO_VALUE); return ImpexTypes.ASSIGN_VALUE; }
}

<WAITING_MACRO_VALUE> {
    {single_string}                                         { return ImpexTypes.SINGLE_STRING; }
    {double_string}                                         { return ImpexTypes.DOUBLE_STRING; }

    {macro_usage}                                           { return ImpexTypes.MACRO_USAGE; }
    {special_parameter_name}                                { return ImpexTypes.HEADER_SPECIAL_PARAMETER_NAME; }

    {left_round_bracket}                                    { return ImpexTypes.ROUND_BRACKETS; }
    {right_round_bracket}                                   { return ImpexTypes.ROUND_BRACKETS; }

    {left_square_bracket}                                   { return ImpexTypes.SQUARE_BRACKETS; }
    {right_square_bracket}                                  { return ImpexTypes.SQUARE_BRACKETS; }

    {assign_value}                                          { return ImpexTypes.ASSIGN_VALUE; }

    {boolean}                                               { return ImpexTypes.BOOLEAN; }
    {digit}                                                 { return ImpexTypes.DIGIT; }
    {field_value_ignore}                                    { return ImpexTypes.FIELD_VALUE_IGNORE; }

    {comma}                                                 { return ImpexTypes.COMMA; }
    {semicolon}                                             { return ImpexTypes.SEMICOLON; }

    {header_mode_insert}                                    { yybegin(HEADER_TYPE); return ImpexTypes.HEADER_MODE_INSERT; }
    {header_mode_update}                                    { yybegin(HEADER_TYPE); return ImpexTypes.HEADER_MODE_UPDATE; }
    {header_mode_insert_update}                             { yybegin(HEADER_TYPE); return ImpexTypes.HEADER_MODE_INSERT_UPDATE; }
    {header_mode_remove}                                    { yybegin(HEADER_TYPE); return ImpexTypes.HEADER_MODE_REMOVE; }

    {macro_value}                                           { return ImpexTypes.MACRO_VALUE; }
}

// Fallback
.                                                           { return TokenType.BAD_CHARACTER; }