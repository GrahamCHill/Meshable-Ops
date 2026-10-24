package com.starter.model.postgres

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*

@Entity @Table(name = "workspace_artwork")
class WorkspaceArtwork(@Id val id: String = "", var icon: String = "folder", var imageVersion: String? = null,
    @get:JsonIgnore @Column(columnDefinition = "BYTEA") var image: ByteArray? = null, @Version var revision: Long = 0)
