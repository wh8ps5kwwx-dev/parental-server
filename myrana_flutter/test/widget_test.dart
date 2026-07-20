import 'package:flutter_test/flutter_test.dart';

import 'package:myrana_flutter/util/child_code_normalizer.dart';

void main() {
  test('ChildCodeNormalizer cleans and strips CHILD prefix for API', () {
    expect(ChildCodeNormalizer.forApi('CHILD-ab12'), 'AB12');
    expect(ChildCodeNormalizer.forApi('ab12'), 'AB12');
    expect(ChildCodeNormalizer.normalize('ab12cd34'), 'CHILDAB12CD34');
  });
}
