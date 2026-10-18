package cn.edu.ubaa.plus.ui.navigation

import cn.edu.ubaa.plus.ui.common.components.BottomNavTab

/**
 * 屏幕与底部 Tab 的对应关系。
 *
 * 「功能」承载全部业务页面，「个人中心」承载设置与关于，避免两处维护相同映射。
 */
internal fun bottomTabFor(screen: AppScreen): BottomNavTab? =
    when (screen) {
      AppScreen.HOME -> BottomNavTab.HOME
      AppScreen.PROFILE,
      AppScreen.SETTINGS,
      AppScreen.ABOUT,
      AppScreen.FEATURE_ORDER -> BottomNavTab.PROFILE
      AppScreen.REGULAR,
      AppScreen.SCHEDULE,
      AppScreen.EXAM,
      AppScreen.GRADE,
      AppScreen.COURSE_DETAIL,
      AppScreen.CLASSROOM_QUERY,
      AppScreen.SPOC_ASSIGNMENTS,
      AppScreen.SPOC_ASSIGNMENT_DETAIL,
      AppScreen.JUDGE_ASSIGNMENTS,
      AppScreen.JUDGE_ASSIGNMENT_DETAIL,
      AppScreen.LIBBOOK_RESERVE,
      AppScreen.LIBBOOK_BOOKINGS,
      AppScreen.BYKC_COURSES,
      AppScreen.BYKC_DETAIL,
      AppScreen.BYKC_CHOSEN,
      AppScreen.BYKC_STATISTICS,
      AppScreen.ICLASS_HOME,
      AppScreen.CGYY_RESERVE_PICKER,
      AppScreen.CGYY_RESERVE_FORM,
      AppScreen.CGYY_ORDERS,
      AppScreen.CGYY_LOCK_CODE,
      AppScreen.EVALUATION,
      AppScreen.YGDK_HOME,
      AppScreen.YGDK_FORM,
      AppScreen.VENUE_HOME,
      AppScreen.DORM_ELECTRICITY,
      AppScreen.CAMPUS_NETWORK,
      AppScreen.CAMPUS_CARD,
      AppScreen.LIBRARY_BORROW -> BottomNavTab.REGULAR
    }
