import { testA2 } from './test';

const addNumber = (a: number, b: number): number => {
  return a + b;
  // return a + b + 1000;
}

export function testB() {
  console.log('B-v2')
  testA2();
}

function helloWord(target: any) {
  console.log('hello Word!');
}

@helloWord
class Test {

}


export {
  addNumber,
  Test,
}