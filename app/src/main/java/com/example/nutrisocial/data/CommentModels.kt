package com.example.nutrisocial.data

/** Comentario de una receta (GET/POST /recipes/{id}/comments). */
data class Comment(
    val id: Int,
    val text: String,
    val createdAt: String,
    val authorId: Int,
    val authorName: String
)

/** Comentario resumido que trae cada tarjeta del feed, para enseñarlo sin abrir la lista. */
data class CommentPreview(val id: Int, val text: String, val authorName: String)

fun Comment.toPreview() = CommentPreview(id, text, authorName)

/** Página de comentarios. [total] es el número de comentarios de la receta, no de la página. */
data class CommentsPage(
    val comments: List<Comment> = emptyList(),
    val nextCursor: Int? = null,
    val total: Int = 0
)

data class CreateCommentRequest(val text: String)

/** Máximo que admite el servidor (ver backend/src/social/comments.js). */
const val MAX_COMMENT_LENGTH = 500
