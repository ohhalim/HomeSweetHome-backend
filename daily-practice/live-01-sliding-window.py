"""
Live Practice 01
Pattern: Two Pointers + Sliding Window
Core Problem: BOJ 1806 (Partial Sum) / LeetCode 209 (Minimum Size Subarray Sum)
"""

from typing import Callable, List, Tuple


def min_subarray_len_bruteforce(target: int, nums: List[int]) -> int:
    """
    O(n^2) baseline.
    Useful for understanding before optimization.
    target 이상이 되는 연속 부분배열의 최소 길이를 가장 단순한 방식 브루트포스로 찾는 코드
    """
    n = len(nums) # 배열 길이 저장 
    best = n + 1 # 아직 답을 못찾았다는 표시값으로 아주 큰값을 넣어둠

    for start in range(n): # 시작 인덱스르 0 부터 끝까지 하나씩 잡음
        curr_sum = 0
        for end in range(start, n):
            curr_sum += nums[end]
            if curr_sum >= target:
                best = min(best, end - start + 1)
                break

    return 0 if best == n + 1 else best


def min_subarray_len_sliding_window(target: int, nums: List[int]) -> int:
    """
    TODO (your turn):
    1) Keep left pointer and curr_sum.
    2) Move right pointer from 0 to n-1, adding nums[right].
    3) While curr_sum >= target:
       - update best length
       - subtract nums[left]
       - move left forward
    4) If no valid window, return 0.
    """
    # Replace this with your implementation.
    return -1


def min_subarray_len_sliding_window_reference(target: int, nums: List[int]) -> int:
    """
    O(n) reference implementation.
    Use this only after you try the TODO version.
    """
    n = len(nums)
    best = n + 1
    left = 0
    curr_sum = 0

    for right in range(n):
        curr_sum += nums[right]

        while curr_sum >= target:
            best = min(best, right - left + 1)
            curr_sum -= nums[left]
            left += 1

    return 0 if best == n + 1 else best


CASES: List[Tuple[int, List[int], int]] = [
    (7, [2, 3, 1, 2, 4, 3], 2),
    (4, [1, 4, 4], 1),
    (11, [1, 1, 1, 1, 1, 1, 1, 1], 0),
    (15, [5, 1, 3, 5, 10, 7, 4, 9, 2, 8], 2),
]


def run_cases(name: str, fn: Callable[[int, List[int]], int]) -> None:
    print(f"\n[{name}]")
    for idx, (target, nums, expected) in enumerate(CASES, start=1):
        got = fn(target, nums)
        ok = "PASS" if got == expected else "FAIL"
        print(
            f"case {idx}: target={target}, expected={expected}, got={got} -> {ok}"
        )


if __name__ == "__main__":
    print("Pattern signals:")
    print("- all numbers are positive")
    print("- subarray/contiguous range")
    print("- shortest/longest window style question")

    run_cases("bruteforce O(n^2)", min_subarray_len_bruteforce)
    run_cases("your sliding-window TODO", min_subarray_len_sliding_window)
    run_cases("reference sliding-window O(n)", min_subarray_len_sliding_window_reference)





def min_subarray_len_sliding_window(target: int, nums: List[int]) -> int:
    """
    TODO (your turn):
    1) Keep left pointer and curr_sum.
    2) Move right pointer from 0 to n-1, adding nums[right].
    3) While curr_sum >= target:
       - update best length
       - subtract nums[left]
       - move left forward
    4) If no valid window, return 0.
    # target과 양의 정수 배열 nums가 주어질떄, 합이 target 이상인 연속 부분배열의 최소 길이를 구해라 없으면 0
    """
    n = 
    # Replace this with your implementation.
    return -1