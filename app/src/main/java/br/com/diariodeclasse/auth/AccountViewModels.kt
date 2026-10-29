package br.com.diariodeclasse.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Retained over rotation, emptied at logout/account change. Activity store contains no academic VM. */
class AccountViewModels:ViewModel() {
    private var id:String?=null
    private var scoped:ViewModelStoreOwner?=null
    fun owner(userId:String):ViewModelStoreOwner {
        if(id!=userId) {
            lock();id=userId
            scoped=object:ViewModelStoreOwner{override val viewModelStore=ViewModelStore()}
        }
        return requireNotNull(scoped)
    }
    fun lock(){scoped?.viewModelStore?.clear();scoped=null;id=null}
    override fun onCleared(){lock()}
}
