package br.com.diariodeclasse

import androidx.lifecycle.ViewModel
import br.com.diariodeclasse.auth.AccountViewModels
import org.junit.Assert.*
import org.junit.Test

class AccountViewModelsTest {
    private class Tracked:ViewModel(){var cleared=false;override fun onCleared(){cleared=true}}
    @Test fun rotationKeepsTheSameOwnerButAccountChangeClearsAcademicModels() {
        val stores=AccountViewModels();val a=stores.owner("account-a");val model=Tracked();a.viewModelStore.put("academic",model)
        assertSame(a,stores.owner("account-a"));assertFalse(model.cleared)
        val b=stores.owner("account-b");assertNotSame(a,b);assertTrue(model.cleared);assertTrue(b.viewModelStore.keys().isEmpty())
    }
    @Test fun logoutDropsTheEntireAcademicStore() {
        val stores=AccountViewModels();val model=Tracked();stores.owner("account-a").viewModelStore.put("backup-preview",model)
        stores.lock();assertTrue(model.cleared);assertTrue(stores.owner("account-a").viewModelStore.keys().isEmpty())
    }
}
