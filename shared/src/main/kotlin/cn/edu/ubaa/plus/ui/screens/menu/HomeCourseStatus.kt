package cn.edu.ubaa.plus.ui.screens.menu

import cn.edu.ubaa.plus.model.dto.SigninClassDto
import cn.edu.ubaa.plus.model.dto.TodayClass
import cn.edu.ubaa.plus.model.format.parseUbaaTime
import cn.edu.ubaa.plus.ui.screens.schedule.displayCourseName
import kotlinx.datetime.LocalDateTime

internal enum class HomeCourseStatus {
  PENDING,
  COMPLETE,
  MISSED,
  UNKNOWN,
}

/** Match by name and start time; never attach an ambiguous attendance record to a course. */
internal fun matchHomeSignin(course: TodayClass, classes: List<SigninClassDto>): SigninClassDto? {
  fun normalize(value: String) = value.filterNot { it.isWhitespace() }.lowercase()
  val name = normalize(course.displayCourseName())
  if (name.isEmpty()) return null
  val candidates = classes.filter { normalize(it.courseName) == name }
  val start = parseUbaaTime(course.time)
  val timed = candidates.filter { start != null && parseUbaaTime(it.classBeginTime) == start }
  return when {
    timed.size == 1 -> timed.single()
    timed.isNotEmpty() -> null
    candidates.size == 1 &&
        (start == null || parseUbaaTime(candidates.single().classBeginTime) == null) ->
        candidates.single()
    else -> null
  }
}

internal fun hasUnmatchedHomeSignin(course: TodayClass, classes: List<SigninClassDto>): Boolean {
  val name = course.displayCourseName().filterNot { it.isWhitespace() }.lowercase()
  return name.isNotEmpty() &&
      classes.any { it.courseName.filterNot { ch -> ch.isWhitespace() }.lowercase() == name } &&
      matchHomeSignin(course, classes) == null
}

internal fun homeCourseStatus(
    course: TodayClass,
    signin: SigninClassDto?,
    now: LocalDateTime,
    attendanceKnown: Boolean,
): HomeCourseStatus {
  if (!attendanceKnown) return HomeCourseStatus.UNKNOWN
  if (signin?.signStatus == 1) return HomeCourseStatus.COMPLETE
  if (signin != null && signin.signStatus != 0) return HomeCourseStatus.UNKNOWN
  val end =
      parseUbaaTime(signin?.classEndTime)
          ?: course.time?.substringAfter('-', "")?.let(::parseUbaaTime)
  val ended = end != null && now.time > end
  return when {
    signin != null && ended -> HomeCourseStatus.MISSED
    signin == null && ended -> HomeCourseStatus.COMPLETE
    else -> HomeCourseStatus.PENDING
  }
}

internal data class HomeCourseEntry(
    val course: TodayClass,
    val signin: SigninClassDto?,
    val attendanceKnown: Boolean,
)

internal fun homeCourseEntries(
    courses: List<TodayClass>,
    signinClasses: List<SigninClassDto>,
    attendanceKnown: Boolean,
): List<HomeCourseEntry> {
  val entries =
      courses.map {
        HomeCourseEntry(
            it,
            matchHomeSignin(it, signinClasses),
            attendanceKnown && !hasUnmatchedHomeSignin(it, signinClasses),
        )
      }
  val ids = entries.mapNotNull { it.signin?.courseId }.toSet()
  return (entries +
          signinClasses
              .filter { it.courseId !in ids }
              .map { signin ->
                HomeCourseEntry(
                    TodayClass(
                        signin.courseName,
                        null,
                        "${signin.classBeginTime} - ${signin.classEndTime}",
                        null,
                    ),
                    signin,
                    attendanceKnown,
                )
              })
      .sortedBy { parseUbaaTime(it.course.time)?.toSecondOfDay() ?: Int.MAX_VALUE }
}
