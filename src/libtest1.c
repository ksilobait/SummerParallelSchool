#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "drts.h"

int c_hello(int id)
{
    int argc = 0;
    drts_get_count(id, &argc);
    if (argc != 0)
    {
        drts_error(id, "c_hello: No args expected");
        return 1;
    }
    drts_print(id, "Hello, DRTS!");
    return 0;
}

int c_init(int id)
{
    int argc, val;
    void *df;
    drts_get_count(id, &argc);
    if (argc!=2) {
        drts_error(id, "c_init: 2 arguments expected");
        return 1;
    }
    if(drts_get_int(id, 1, &val)) {
        drts_error(id, "c_init: integer expected");
        return 1;
    }
    
    df=malloc(sizeof(int));
    *(int*)df=val;

    if (drts_set_df(id, 0, df, sizeof(int))) {
        drts_error(id, "c_init: set df failed");
        free(df);
        return 1;
    }
    return 0;
}

int c_show(int id)
{
    int argc;
    void *df;
    int df_size;
    char buf[1024];

    drts_get_count(id, &argc);

    if (argc!=1) {
        drts_error(id, "c_show: 1 argument expected");
        return 1;
    }

    if (drts_get_df(id, 0, &df, &df_size)!=0) {
        drts_error(id, "c_show: get_df failed");
        return 1;
    }

    if (df_size!=sizeof(int)) {
        drts_error(id, "c_show: invalid df size, int expected");
        return 1;
    }

    sprintf(buf, "c_show: %d\n", *(int*)df);
    drts_print(id, buf);
    return 0;
}
