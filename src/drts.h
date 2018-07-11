#ifndef DRTS_H_
#define DRTS_H_

#include <stddef.h>

// Get number of call arguments - TODO
int drts_get_count(int call_id, int *count);

// Get type of an argument. Possible values: “int”, “string”, “df”. - TODO
int drts_get_type(int call_id, int arg_num, const char **type_name);

// Unset value of an argument - TODO
int drts_unset(int call_id, int arg_num);

// Get argument value as int - TODO
int drts_get_int(int call_id, int arg_num, int *result);

// Set argument value as int - TODO
int drts_set_int(int call_id, int arg_num, int value);

// Get argument value as string - TODO
int drts_get_string(int call_id, int arg_num, const char **result);

// Set argument value as string - TODO
int drts_set_string(int call_id, int arg_num, const char *value);

// Get data fragment value - (void*, int) pair - TODO
int drts_get_df(int call_id, int arg_num, void **data, int *size);

// Set data fragment value - (void*, int) pair - TODO
int drts_set_df(int call_id, int arg_num, void *data, int size);

// Display an error message - TODO
void drts_error(int call_id, const char *error_msg);

// Display inforational message - TODO
void drts_print(int call_id, const char *info_msg);

#endif // DRTS_H_
