<#-- templates/default_enum_proto.ftl -->
<#-- This template expects a data model with:
     - protoPackage (String)
     - javaPackage (String)
     - javaOuterClassName (String)
     - globalParams (Map<String, String>) - Currently unused in this specific template
     - enums (List<Map<String, Object>>) where the list contains EXACTLY ONE map with:
         - name (String): The name of the enum type (e.g., "ConditionType")
         - hasZeroId (boolean): Whether ID 0 was explicitly defined
         - params (Map<String, String>): Per-enum parameters - Currently unused
         - members (List<Map<String, Object>>) where each member map has:
             - name (String): The enum constant name (e.g., "ACCEPT_QUEST")
             - id (int): The integer ID
             - comment (String): The description/comment
-->
syntax = "proto3";

<#-- Use protoPackage if available -->
<#if protoPackage?? && protoPackage?has_content>
package ${protoPackage};
</#if>

<#-- Standard Java options -->
<#if javaPackage?? && javaPackage?has_content>
option java_package = "${javaPackage}";
</#if>
option java_multiple_files = true;

<#-- Access the single enum definition provided in the list -->
<#if enums?has_content> <#-- Check if the list is not empty -->
    <#assign enum = enums[0]> <#-- Get the first (and only) element -->

<#-- Example: Accessing a per-enum parameter (if you were to use it) -->
<#-- <#if enum.params.isBeta?? && enum.params.isBeta == "true">
// BETA Enum Feature
</#if> -->

enum ${enum.name} {
<#-- Ensure UNSPECIFIED = 0 exists if not user-defined -->
<#if !enum.hasZeroId>
    // Default unspecified value added by processor.
    ${enum.name?upper_case}_UNSPECIFIED = 0;
</#if>

<#-- Loop through members of the current enum -->
<#list enum.members as member>
<#-- Add comment if present -->
    <#if member.comment?has_content>
    // ${member.comment}
    </#if>
    ${member.name} = ${member.id?c}; <#-- Use ?c to output numbers reliably -->
</#list> <#-- End of member loop -->
} <#-- End of enum definition -->
<#else>
<#-- This part should ideally not be reached if validation works -->
    // ERROR: No enum data provided to the template!
</#if>

<#-- Example: Global footer comment -->
<#-- <#if globalParams.generationTimestamp??>
// Generated at: ${globalParams.generationTimestamp}
</#if> -->