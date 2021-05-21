package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.MinHeap;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Beginnings of a MinHeap, for Stage 4 of project.
 * Does not include the additional data structure or logic needed to reheapify an element after its last use time changes.
 * Note: Many methods used are in MinHeap, the abstract class.
 * @param <E>
 */
public class MinHeapImpl<E extends Comparable<E>> extends MinHeap<E> {

    /**
     * Constructor for MinHeap
     */
    public MinHeapImpl () {
        this.elements = (E[]) new Comparable[8];
    }

    /**
     * "ReHeapify"'s the given element, meaning the element is put in its proper place
     * @param element
     */
    @Override
    public void reHeapify (E element) {
        if (element == null) {
            throw new IllegalArgumentException("Element must not be null.");
        }
        int e = this.getArrayIndex(element);
        this.upHeap(e);
        this.downHeap(e);
    }

    /**
     * Returns the index in the Array for the specified element.
     * @param element
     * @return index. Throws exceptions if there are errors.
     */
    @Override
    protected int getArrayIndex (E element) {
        if (element == null) {
            throw new IllegalArgumentException("Element must not be null.");
        }
        if (this.isEmpty()) {
            throw new NoSuchElementException("Heap is empty.");
        }
        for (int i = 1; i < this.elements.length; i++) {
            if (this.elements[i] != null && this.elements[i].equals(element)) {
                return i;
            }
        }
        throw new NoSuchElementException("Heap does not contain this element.");
    }

    /**
     * Doubles the Array size while keeping all the old elements in their previous place
     */
    @Override
    protected void doubleArraySize () {
        this.elements = Arrays.copyOf(this.elements, this.elements.length*2);
    }
}