package com.starter.service

import com.starter.controller.*
import com.starter.exception.*
import com.starter.model.postgres.*
import com.starter.plugins.PluginActor
import com.starter.repository.postgres.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class AccountProfilesTest {
    private val actors=mock(PluginActor::class.java);private val users=mock(UserRepository::class.java);private val rows=mock(UserProfileRepository::class.java)
    private val user=User(email="member@example.test",username="member",role="USER");private var row:UserProfile?=null
    private val service=AccountProfiles(actors,users,rows)
    init {`when`(actors.fresh(user.email)).thenReturn(user);`when`(rows.findById(user.id)).thenAnswer{Optional.ofNullable(row)};`when`(rows.saveAndFlush(any(UserProfile::class.java))).thenAnswer{row=it.getArgument(0);row};`when`(users.saveAndFlush(any(User::class.java))).thenAnswer{it.arguments[0]}}
    @Test fun `profile edits preserve identity role and access permissions`() {
        val result=service.save(user.email,ProfileInput("New name","+27 82 123 4567","Africa/Johannesburg"))
        assertThat(result.name).isEqualTo("New name");assertThat(result.email).isEqualTo(user.email);assertThat(result.role).isEqualTo("USER")
        assertThat(result.timeZone).isEqualTo("Africa/Johannesburg")
        assertThat(result.contactNumber).isEqualTo("+27821234567")
    }
    @Test fun `stale edits invalid timezone and malformed images do not persist`() {
        row=UserProfile(user.id,revision=2)
        assertThatThrownBy{service.save(user.email,ProfileInput("Name",revision=0))}.isInstanceOf(ConflictException::class.java)
        assertThatThrownBy{service.save(user.email,ProfileInput("Name",timeZone="Imaginary/Place"))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy{service.avatar(user.email,"<svg/>".toByteArray())}.isInstanceOf(BadRequestException::class.java)
        verify(users,never()).saveAndFlush(any(User::class.java));verify(rows,never()).saveAndFlush(any(UserProfile::class.java))
    }
    @Test fun `avatar is normalized and removable without exposing other accounts`() {
        val bytes=ByteArrayOutputStream().also{ImageIO.write(BufferedImage(8,16,BufferedImage.TYPE_INT_RGB),"png",it)}.toByteArray()
        assertThat(service.avatar(user.email,bytes).avatarVersion).isNotBlank()
        assertThat(ImageIO.read(service.image(user.email).inputStream()).width).isEqualTo(64)
        assertThat(service.avatar(user.email,null).avatarVersion).isNull()
        assertThatThrownBy{service.image(user.email)}.isInstanceOf(NotFoundException::class.java)
    }
}
