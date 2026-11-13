package ai.heartz.app.ui.components

import ai.heartz.app.platform.profile.PleosProfile
import ai.heartz.app.ui.theme.Hz
import ai.heartz.app.ui.theme.HzFont
import ai.heartz.app.ui.theme.colors
import ai.heartz.core.model.StoryTone
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pleos Connect 상태 표시줄 프로필과 같은 모양: 흰 원, 회색 테두리, 보라색 첫 글자 */
private val ProfileRing = Color(0xFFB0B0B0)
private val ProfileLetter = Color(0xFF9C27B0)

/** 닉네임 첫 글자 프로필 원(보낸 이·내 프로필·목록 공통) */
@Composable
fun ProfileCircle(name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(Color.White)
            .border((size.value * 0.025f).coerceAtLeast(1f).dp, ProfileRing, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            PleosProfile.initial(name),
            color = ProfileLetter,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = HzFont, fontWeight = FontWeight.Medium, fontSize = (size.value * 0.46f).sp, lineHeight = (size.value * 0.5f).sp),
            maxLines = 1,
        )
    }
}

/** 목록용: 프로필 원 + 오른쪽 아래 사연 색 표시 */
@Composable
fun ProfileTile(name: String, tone: StoryTone, size: Dp, modifier: Modifier = Modifier, badge: Boolean = true) {
    Box(modifier.size(size)) {
        ProfileCircle(name, size)
        if (badge) {
            val b = size * 0.36f
            Box(
                Modifier.align(Alignment.BottomEnd).offset(x = b * 0.12f, y = b * 0.12f).size(b)
                    .clip(CircleShape).background(tone.colors().badge).border(3.dp, Hz.Bg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.GraphicEq, null, tint = Hz.Ink, modifier = Modifier.size(b * 0.6f))
            }
        }
    }
}
