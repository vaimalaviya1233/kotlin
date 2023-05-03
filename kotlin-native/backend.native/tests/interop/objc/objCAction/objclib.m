#include "objclib.h"

#include <Foundation/NSKeyValueCoding.h>

void execute(id target, SEL selector) {
    [target performSelector:selector];
}

void setProxy(id target, id proxy) {
    [target setValue:proxy forKey:@"proxy"];
}
