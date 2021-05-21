package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.Stack;

/**
 * @param <T>
 */
public class StackImpl<T> implements Stack<T> {

    private Node<T> top;
    private int count;

    private static final class Node<T> {
        
        private T current;
        private Node<T> next;

        private Node (T item) {
            if (item == null) {
                throw new IllegalArgumentException("Item must not be null.");
            }
            this.current = item;
            this.next = null;
        }
    }

    /**
     * constructor for Stack
     */
    public StackImpl () {
        this.top = null;
        this.count = 0;
    }
    /**
     * @param element object to add to the Stack
     */
    @Override
    public void push (T element) {
        if (element == null) {
            throw new IllegalArgumentException ("Element must not be null.");
        }
        Node<T> newItem = new Node<>(element);
        if (this.top != null) {
            Node<T>oldTop = this.top;
            this.top = newItem;
            newItem.next = oldTop;
        }
        else {
            this.top = newItem;
        }
        this.count++;
    }

    /**
     * removes and returns element at the top of the stack
     * @return element at the top of the stack, null if the stack is empty
     */
    @Override
    public T pop () {
        if(this.top == null) {
            return null;
        }
        T returnItem = this.top.current;
        this.top = this.top.next;
        this.count--;
        return returnItem;
    }

    /**
     * @return the element at the top of the stack without removing it
     */
    @Override
    public T peek () {
        if(this.top == null) {
            return null;
        }
        return this.top.current;
    }

    /**
     * @return how many elements are currently in the stack
     */
    @Override
    public int size () {
        return this.count;
    }
}