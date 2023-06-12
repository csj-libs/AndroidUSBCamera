/*
 * Copyright 2017-2023 Jiangdg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jiangdg.ausbc.base

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/** Base fragment
 *
 * @author Created by jiangdg on 2022/1/21
 */
abstract class BaseView : FrameLayout {
    private var isAlive = false
    private var mRootView: View? = null
    protected lateinit var mContext: Context

    fun init(context: Context, attrs: AttributeSet?) {
        this.mContext=context
        val inflater = LayoutInflater.from(context)
        getRootView(inflater, this).apply {
            mRootView = this
        }
        initView(context,attrs)
        initData()
    }

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr){
        init(context, attrs)
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAlive = true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAlive = false
        clear()
        mRootView = null
    }


    open fun isFragmentAttached(): Boolean {
        return isAlive
    }

    protected abstract fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View?
    protected open fun initView(context: Context, attrs: AttributeSet?) {}
    protected open fun initData() {}
    protected open fun clear() {}
}