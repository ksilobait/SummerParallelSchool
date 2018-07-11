#include "drts.h"
#include <jni.h>
#include <string.h>

int drts_get_count(int call_id, int *count)
{
    JavaVMOption options[1];
    options[0].optionString = "-Djava.class.path=."; //current directory
    
    JavaVMInitArgs vm_args; //initialization arguments for the JVM
    memset(&vm_args, 0, sizeof(vm_args));
    vm_args.version = JNI_VERSION_1_2;
    vm_args.nOptions = 1;
    vm_args.options = options;
    
    JNIEnv* env; //JNI execution environment
    JavaVM* jvm; //pointer to the JVM
    long status = JNI_CreateJavaVM(&jvm, (void**)&env, &vm_args);
    if (status == JNI_ERR)
    {
        return -1;
    }
    
    int to_return = -1; //args number
    
    jclass cls = (*env)->FindClass(env, "DRTS");
    if (cls != 0)
    {
        jmethodID mid = (*env)->GetMethodID(env, cls, "java_get_count", "(I)I");
        if (mid != 0)
        {
            to_return = (int) ((*env)->CallIntMethod(env, cls, mid, call_id));
        }
    }

    (*jvm)->DestroyJavaVM(jvm);
    return to_return;
}

int main()
{
    return 0;
}
