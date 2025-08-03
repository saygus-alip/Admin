package com.alip.admin.Internet

import com.google.gson.annotations.SerializedName

data class Post(
    val userId: Int,
    val id: Int,
    val title: String,
    @SerializedName("body")
    val content: String // เปลี่ยนชื่อตัวแปรให้เข้าใจง่ายขึ้น
)