package br.com.diariodeclasse.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider

/** Retained over rotation, emptied at logout/account change. Activity store contains no academic VM. */
class AccountViewModels:ViewModel() {
    private var id:String?=null
    private var scoped:ViewModelStoreOwner?=null
    fun owner(userId:String,factory:ViewModelProvider.Factory):ViewModelStoreOwner {
        if(id!=userId) {
            lock();id=userId
            scoped=object:ViewModelStoreOwner,HasDefaultViewModelProviderFactory {
                override val viewModelStore=ViewModelStore()
                override val defaultViewModelProviderFactory=factory
            }
        }
        return requireNotNull(scoped)
    }
    fun lock(){scoped?.viewModelStore?.clear();scoped=null;id=null}
    override fun onCleared(){lock()}
}
