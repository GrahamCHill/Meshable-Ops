package com.starter.plugins

internal object PluginSharedSettingsValidation {
    fun validate(m:PluginManifest) {
        val key=Regex("[a-z][A-Za-z0-9]{0,39}");val id=Regex("[a-z][a-z0-9-]{0,39}")
        fun menu(category:String,label:String,categoryLabel:String) = require(category.matches(Regex("[a-z][a-z0-9-]{1,59}"))&&label.isNotBlank()&&label.length<=100&&categoryLabel.length<=80&&listOf(label,categoryLabel).none {it.any(Char::isISOControl)}) {"Use valid settings category labels"}
        require(m.settingsSections.size<=10&&m.settingsSections.map {it.id}.distinct().size==m.settingsSections.size)
        val fieldKeys=m.settingsFields.map {it.key}.toSet()
        require(m.settingsSections.flatMap {it.fields}.distinct().size==m.settingsSections.sumOf {it.fields.size}) {"A field belongs to only one settings section"}
        m.settingsSections.forEach {s->require(s.id.matches(id)&&s.description.length<=600&&s.description.none(Char::isISOControl)&&s.fields.all {it in fieldKeys});menu(s.category,s.label,s.categoryLabel)}
        require(m.sharedSettings.size<=10&&m.sharedSettings.map {it.id}.distinct().size==m.sharedSettings.size)
        m.sharedSettings.forEach {r->require(r.id.matches(id)&&r.schemaId.matches(Regex("[a-z][a-z0-9.-]{1,99}"))&&r.label.isNotBlank()&&r.label.length<=100&&r.fields.isNotEmpty()&&r.fields.size<=30&&r.fields.all {it.matches(key)}&&(r.sectionId.isEmpty()||m.settingsSections.any {it.id==r.sectionId})) {"Use a declared shared connection schema, fields and settings section"}}
        require(m.sharedSettingsSchemas.size<=5&&m.sharedSettingsSchemas.map {it.id}.distinct().size==m.sharedSettingsSchemas.size)
        m.sharedSettingsSchemas.forEach {s->
            require(s.id.startsWith("plugin.${m.id}.")&&s.id.matches(Regex("[a-z][a-z0-9.-]{1,99}"))&&s.label.isNotBlank()&&s.label.length<=100&&s.description.length<=600) {"Shared schemas must use their plugin's namespace"}
            PluginManifests.validateFields(s.fields);menu(s.adminMenu.category,s.label,s.adminMenu.categoryLabel)
            require(s.adminMenu.label.length<=100&&s.adminMenu.label.none(Char::isISOControl)&&(s.adminMenu.icon==null||s.adminMenu.icon in com.starter.service.WorkspaceArtworkService.ICONS)) {"Use a valid shared settings menu label and built-in icon"}
            require(s.identityFields.all {key->s.fields.any {it.key==key&&it.type!=PluginFieldType.SECRET}}) {"Use non-secret identity fields"}
            require(s.httpOriginField==null||s.httpOriginField in s.identityFields&&s.fields.any {it.key==s.httpOriginField&&it.type==PluginFieldType.TEXT&&it.required}) {"An HTTP shared credential needs a required non-secret origin identity field"}
        }
        require(m.settingsSections.isEmpty()&&m.sharedSettings.isEmpty()&&m.sharedSettingsSchemas.isEmpty()||m.hasAdminSettings&&PluginExtension.SETTINGS in m.extensions) {"Split and shared settings require administrator settings support"}
    }
}
