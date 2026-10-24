package com.starter.repository.postgres

import com.starter.model.postgres.WorkspaceArtwork
import org.springframework.data.jpa.repository.JpaRepository

interface ArtworkMetadata {val id:String;val icon:String;val imageVersion:String?;val revision:Long}
interface WorkspaceArtworkRepository : JpaRepository<WorkspaceArtwork,String> {
    fun findAllProjectedByIdIn(ids:Collection<String>):List<ArtworkMetadata>
}
