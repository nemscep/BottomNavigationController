/*
 * Copyright (c) 2020. Created by Nemanja Scepanovic
 */

package com.nemscep.bottomnavigationcontroller.controller

import android.util.SparseArray
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.forEach
import androidx.core.util.set
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.*
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nemscep.bottomnavigationcontroller.backstack.*
import com.nemscep.bottomnavigationcontroller.util.menuItemList
import java.lang.IllegalStateException

class BottomNavigationControllerImpl private constructor(
    private val mBottomNavigationView: BottomNavigationView,
    private val mActivity: AppCompatActivity,
    private val mContainerView: View,
    private val mGraphIdsList: List<Int>
) : BottomNavigationController {
    private val mFragmentManager by lazy { mActivity.supportFragmentManager }
    private val mBackStack: DefaultNavigationBackStack = DefaultNavigationBackStack
    private val fragmentDestinationIdMap = SparseArray<String>()

    private val _currentNavController = MutableLiveData<NavController>()
    override val currentNavController =
        _currentNavController.distinctUntilChanged() as LiveData<NavController>

    private fun onBackPressed() {
        val currentFragment =
            mFragmentManager.findFragmentByTag(mBackStack.peek()) as NavHostFragment
        if (!currentFragment.navController.navigateUp()) {
            if (mBackStack.size() > 1) {
                // remove current position from stack
                mBackStack.pop()
                val newTop = mFragmentManager.findFragmentByTag(mBackStack.peek())!!
                // set the next item in stack as current
                mBottomNavigationView.selectedItemId = newTop.findNavController().graph.id
            } else {
                mActivity.finish()
            }
        }
    }

    private fun createNavHostFragments() {
        mGraphIdsList.forEachIndexed { index, graphId ->
            val tag = getFragmentTag(index)
            val navHostFragment =
                obtainNavHostFragment(mFragmentManager, tag, graphId, mContainerView.id)

            val fragmentDestinationId = navHostFragment.navController.graph.id
            fragmentDestinationIdMap[fragmentDestinationId] = tag

            if (mBottomNavigationView.selectedItemId == fragmentDestinationId) {
                // push BottomNavigationView's current item to stack
                mBackStack.push(navHostFragment.tag!!)
                // Update livedata with the selected graph
                _currentNavController.value = navHostFragment.navController
                attachNavHostFragment(mFragmentManager, navHostFragment, mBackStack)
            } else {
                detachNavHostFragment(mFragmentManager, navHostFragment)
            }
        }
    }

    private fun configureBottomNavigationView() {
        mBottomNavigationView.setOnNavigationItemSelectedListener { item ->
            if (mFragmentManager.isStateSaved) return@setOnNavigationItemSelectedListener false

            val newlySelectedItemTag = fragmentDestinationIdMap[item.itemId]
            val selectedFragment =
                mFragmentManager.findFragmentByTag(newlySelectedItemTag)
                        as NavHostFragment
            // push currently selected tag to backstack
            mBackStack.push(newlySelectedItemTag)

            // attach currently selected fragment and detach others
            mFragmentManager.beginTransaction()
                .attach(selectedFragment)
                .setPrimaryNavigationFragment(selectedFragment)
                .apply {
                    // Detach all other Fragments
                    fragmentDestinationIdMap.forEach { _, tag ->
                        if (tag != newlySelectedItemTag) {
                            detach(mFragmentManager.findFragmentByTag(tag)!!)
                        }
                    }
                }
                .setReorderingAllowed(true)
                .commit()

            _currentNavController.value = selectedFragment.navController
            return@setOnNavigationItemSelectedListener true
        }
        mBottomNavigationView.setOnNavigationItemReselectedListener { item ->
            val newlySelectedItemTag = fragmentDestinationIdMap[item.itemId]
            val selectedFragment = mFragmentManager.findFragmentByTag(newlySelectedItemTag)
                    as NavHostFragment
            val navController = selectedFragment.navController
            // Pop the back stack to the start destination of the current navController graph
            navController.popBackStack(navController.graph.startDestination, false)
        }
    }

    private fun configureActivity() {
        mActivity.onBackPressedDispatcher.addCallback { onBackPressed() }
    }

    class Builder {
        private lateinit var bottomNavigationView: BottomNavigationView
        private lateinit var activity: AppCompatActivity
        private lateinit var graphIds: List<Int>
        private lateinit var fragmentContainerView: FragmentContainerView

        fun bindBottomNavigationView(bottomNavigationView: BottomNavigationView) =
            apply { this.bottomNavigationView = bottomNavigationView }

        fun bindNavGraphs(vararg graphId: Int) = apply {
            val graphsList = mutableListOf<Int>()
            for (id in graphId) {
                graphsList.add(id)
            }
            if (graphsList.size != bottomNavigationView.menuItemList().size) {
                throw IllegalStateException("Graph ids list passed isn't size compatible with menu items ")
            }
            graphIds = graphsList
        }

        fun bindFragmentContainerView(fragmentContainerView: FragmentContainerView) =
            apply { this.fragmentContainerView = fragmentContainerView }

        fun bindActivity(activity: AppCompatActivity) = apply { this.activity = activity }

        private fun checkRequiredParamsBound() {
            when {
                !::bottomNavigationView.isInitialized -> throw IllegalStateException("BottomNavigationView must be bound!")
                !::activity.isInitialized -> throw IllegalStateException("Activity must be bound!")
                !::graphIds.isInitialized -> throw IllegalStateException("GraphIds list must be bound!")
                !::fragmentContainerView.isInitialized -> throw IllegalStateException("fragmentContainerView must be bound!")
            }
        }

        fun build(): BottomNavigationController {
            // Before creating and object, all required attributes must be bound
            checkRequiredParamsBound()

            return BottomNavigationControllerImpl(
                bottomNavigationView,
                activity,
                fragmentContainerView,
                graphIds
            ).apply {
                createNavHostFragments()
                configureActivity()
                configureBottomNavigationView()
            }
        }
    }

}



