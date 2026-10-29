package br.com.diariodeclasse

import androidx.lifecycle.ViewModel
import br.com.diariodeclasse.auth.AccountViewModels
import org.junit.Assert.*
import org.junit.Test

class AccountViewModelsTest {
    private val factory=androidx.lifecycle.ViewModelProvider.NewInstanceFactory()
    private class Tracked:ViewModel(){var cleared=false;override fun onCleared(){cleared=true}}
    private class RequiresDependency(val dependency:String):ViewModel()
    @Test fun rotationKeepsTheSameOwnerButAccountChangeClearsAcademicModels() {
        val stores=AccountViewModels();val a=stores.owner("account-a",factory);val model=Tracked();a.viewModelStore.put("academic",model)
        assertSame(a,stores.owner("account-a",factory));assertFalse(model.cleared)
        val b=stores.owner("account-b",factory);assertNotSame(a,b);assertTrue(model.cleared);assertTrue(b.viewModelStore.keys().isEmpty())
    }
    @Test fun logoutDropsTheEntireAcademicStore() {
        val stores=AccountViewModels();val model=Tracked();stores.owner("account-a",factory).viewModelStore.put("backup-preview",model)
        stores.lock();assertTrue(model.cleared);assertTrue(stores.owner("account-a",factory).viewModelStore.keys().isEmpty())
    }
    @Test fun scopedOwnerUsesTheSuppliedFactoryInsteadOfAZeroArgumentConstructor() {
        var creations=0
        val injected=object:androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T {
                creations++;return RequiresDependency("application-equivalent") as T
            }
        }
        val stores=AccountViewModels();val owner=stores.owner("account-a",injected)
        val first=androidx.lifecycle.ViewModelProvider(owner)[RequiresDependency::class.java]
        assertEquals("application-equivalent",first.dependency)
        assertSame(first,androidx.lifecycle.ViewModelProvider(stores.owner("account-a",injected))[RequiresDependency::class.java])
        assertEquals(1,creations)
    }
}
