#include "Server.h"

JNIEXPORT void JNICALL Java_Server_cLaunchMethod (JNIEnv* env, jobject obj, jstring string1, jstring string2, jobjectArray array)
{
	const char* dll = (*env)->GetStringUTFChars(env, string1, 0);
	const char* codeName = (*env)->GetStringUTFChars(env, string2, 0);
	int len = (*env)->GetArrayLength(env, array);
	for (int i = 0; i < len; ++i)
	{
		jstring jstr = (*env)->GetObjectArrayElement(env, array, i);
		const char* argI = (*env)->GetStringUTFChars(env, jstr, 0);
		//TODO: ReleaseStringUTFChars
	}
}

void main()
{
	
}
