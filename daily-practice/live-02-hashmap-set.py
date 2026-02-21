"""
Live Practice 02
Pattern: HashMap / Set
"""

from typing import Dict, List, Tuple


def contains_duplicate(nums: List[int]) -> bool:
    """
    TODO:
    nums에 중복된 값이 있으면 True, 아니면 False를 반환하세요.
    """
    seen = set()
    for num in nums:
        if num in seen:
            return True
        seen.add(num)
    return False


def two_sum(nums: List[int], target: int) -> List[int]:
    """
    TODO:
    합이 target이 되는 두 수의 인덱스를 반환하세요.
    정답이 항상 하나 존재한다고 가정합니다.
    """
    seen =
    see
    return []


def first_unique_char(s: str) -> int:
    """
    TODO:
    처음으로 한 번만 등장하는 문자의 인덱스를 반환하세요.
    없으면 -1을 반환하세요.
    """
    return -1


def contains_duplicate_reference(nums: List[int]) -> bool:
    seen = set()
    for num in nums:
        if num in seen:
            return True
        seen.add(num)
    return False


def two_sum_reference(nums: List[int], target: int) -> List[int]:
    seen: Dict[int, int] = {}
    for i, num in enumerate(nums):
        need = target - num
        if need in seen:
            return [seen[need], i]
        seen[num] = i
    return []


def first_unique_char_reference(s: str) -> int:
    freq: Dict[str, int] = {}
    for ch in s:
        freq[ch] = freq.get(ch, 0) + 1

    for i, ch in enumerate(s):
        if freq[ch] == 1:
            return i
    return -1


def run_contains_duplicate_cases() -> None:
    cases: List[Tuple[List[int], bool]] = [
        ([1, 2, 3, 1], True),
        ([1, 2, 3, 4], False),
        ([], False),
    ]
    print("\n[contains_duplicate]")
    for i, (nums, expected) in enumerate(cases, start=1):
        got = contains_duplicate(nums)
        ok = "PASS" if got == expected else "FAIL"
        print(f"case {i}: expected={expected}, got={got} -> {ok}")

    print("\n[contains_duplicate_reference]")
    for i, (nums, expected) in enumerate(cases, start=1):
        got = contains_duplicate_reference(nums)
        ok = "PASS" if got == expected else "FAIL"
        print(f"case {i}: expected={expected}, got={got} -> {ok}")


def run_two_sum_cases() -> None:
    cases: List[Tuple[List[int], int, List[int]]] = [
        ([2, 7, 11, 15], 9, [0, 1]),
        ([3, 2, 4], 6, [1, 2]),
        ([3, 3], 6, [0, 1]),
    ]
    print("\n[two_sum]")
    for i, (nums, target, expected) in enumerate(cases, start=1):
        got = two_sum(nums, target)
        ok = "PASS" if got == expected else "FAIL"
        print(f"case {i}: expected={expected}, got={got} -> {ok}")

    print("\n[two_sum_reference]")
    for i, (nums, target, expected) in enumerate(cases, start=1):
        got = two_sum_reference(nums, target)
        ok = "PASS" if got == expected else "FAIL"
        print(f"case {i}: expected={expected}, got={got} -> {ok}")


def run_first_unique_char_cases() -> None:
    cases: List[Tuple[str, int]] = [
        ("leetcode", 0),
        ("loveleetcode", 2),
        ("aabb", -1),
    ]
    print("\n[first_unique_char]")
    for i, (s, expected) in enumerate(cases, start=1):
        got = first_unique_char(s)
        ok = "PASS" if got == expected else "FAIL"
        print(f"case {i}: expected={expected}, got={got} -> {ok}")

    print("\n[first_unique_char_reference]")
    for i, (s, expected) in enumerate(cases, start=1):
        got = first_unique_char_reference(s)
        ok = "PASS" if got == expected else "FAIL"
        print(f"case {i}: expected={expected}, got={got} -> {ok}")


if __name__ == "__main__":
    print("HashMap/Set pattern signals:")
    print("- 중복 검사")
    print("- 값의 등장 횟수")
    print("- target - num 형태의 보완값 탐색")

    run_contains_duplicate_cases()
    run_two_sum_cases()
    run_first_unique_char_cases()
