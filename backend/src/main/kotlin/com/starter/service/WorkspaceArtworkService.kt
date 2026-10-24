package com.starter.service

import com.starter.exception.*
import com.starter.model.postgres.WorkspaceArtwork
import com.starter.repository.postgres.WorkspaceArtworkRepository
import com.starter.service.branding.BrandingImages
import com.starter.service.branding.BrandingImageProfile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

data class ArtworkView(val icon:String="folder",val imageVersion:String?=null,val revision:Long = -1)
data class ArtworkInput(val icon:String,val revision:Long = -1)

/** Shared raster processing only; each calling controller enforces its domain's authorization. */
@Service
class WorkspaceArtworkService(private val repository:WorkspaceArtworkRepository,private val jdbc:JdbcTemplate) {
    fun current(id:String,default:String="folder") = repository.findById(id).orElse(null)?.let(::view) ?: ArtworkView(default)
    fun many(ids:Collection<String>) = if(ids.isEmpty())emptyMap() else repository.findAllProjectedByIdIn(ids).associate {it.id to ArtworkView(it.icon,it.imageVersion,it.revision)}
    fun image(id:String,version:String):ByteArray = repository.findById(id).orElse(null)?.takeIf {it.imageVersion==version}?.image ?: throw NotFoundException("Image unavailable")
    @Transactional fun preset(id:String,input:ArtworkInput):ArtworkView {
        if(input.icon !in ICONS)throw BadRequestException("Choose one of the available icons")
        val row=locked(id,input.revision);row.icon=input.icon;row.image=null;row.imageVersion=null
        return view(repository.saveAndFlush(row))
    }
    @Transactional fun upload(id:String,revision:Long,file:MultipartFile):ArtworkView {
        if(file.isEmpty||file.size>BrandingImageProfile.FAVICON.maxBytes)throw BadRequestException("Choose a PNG, JPEG or WebP up to 1 MiB")
        val bytes=BrandingImages.normalize(file.bytes,BrandingImageProfile.FAVICON)
        val row=locked(id,revision);row.image=bytes;row.imageVersion=UUID.randomUUID().toString()
        return view(repository.saveAndFlush(row))
    }
    private fun locked(id:String,revision:Long):WorkspaceArtwork {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))",RowCallbackHandler {},"workspace-artwork:$id")
        val row=repository.findById(id).orElse(null)
        if((row?.revision ?: -1)!=revision)throw ConflictException("This icon changed. Reload it before saving.")
        return row ?: WorkspaceArtwork(id)
    }
    private fun view(row:WorkspaceArtwork)=ArtworkView(row.icon,row.imageVersion,row.revision)
    companion object {val ICONS=setOf("folder","briefcase","code","building","layers","rocket","target","chart","globe","calendar","users","shield","settings","plug","database","mail","workflow","search","file","layout","cloud","lock","activity")}
}
