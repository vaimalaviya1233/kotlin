#import "propertyOverloadByType.h"

@implementation InterfaceBase
- (instancetype)initWith:(InterfaceBase*)base {
    self = [super init];
    if (self) {
        _delegate = [base copy]; // Assign the value directly to the instance variable (_delegate), bypassing the setter
    }
    return self;
}
@end

@implementation InterfaceDerived
@end

@implementation InterfaceIntegerProperty
@end
