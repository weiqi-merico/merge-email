package com.merico.selenium.page.factory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.LazyLoader;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.pagefactory.Annotations;
import org.openqa.selenium.support.pagefactory.FieldDecorator;

import com.merico.selenium.control.Control;
import com.merico.selenium.control.LazyLoadControl;

public class LazyLoadFieldDecorator implements FieldDecorator {
	protected WebDriver driver;

	public LazyLoadFieldDecorator(WebDriver driver) {
		this.driver = driver;
	}

	public LazyLoadFieldDecorator() {
		// TODO Auto-generated constructor stub
	}

	public Object decorate(ClassLoader loader, final Field field) {
		// It is instantiated if it is a WebElement directly
		if(field.getType().equals(WebElement.class)){
			Annotations annotations = new Annotations(field);
			By by = annotations.buildBy();
			return driver.findElement(by);
		}
		if (!Control.class.isAssignableFrom(field.getType())) {
			return null;
		}
		Annotations annotations = new Annotations(field);
		final By by = annotations.buildBy();
		if (this.isLazyLoad(field)) {
			Enhancer enhancer = new Enhancer();
			enhancer.setSuperclass(field.getType());
			enhancer.setCallback(new LazyLoader() {

				public Object loadObject() throws Exception {
					WebElement element = driver.findElement(by);
					Object lazycontrol = instantiateControl(element,
							field.getType());
					return lazycontrol;
				}
			});
			return enhancer.create();
		} else {
			WebElement element = driver.findElement(by);
			return instantiateControl(element, field.getType());
		}
	}
	
	/**
	 * Judge field type or its parent until the Object, whether or not marked LazyLoadControl
	 * 
	 * @param field
	 */
	private boolean isLazyLoad(Field field) {
		boolean result = false;
		Class<?> fieldClass = field.getType();
		
		while(!fieldClass.equals(Object.class)) {
			result = fieldClass.isAnnotationPresent(LazyLoadControl.class);
			
			if (result) {
				break;
			}
			else {
				fieldClass = fieldClass.getSuperclass();
			}
		}
		
		return result;
	}

	private Object instantiateControl(WebElement element, Class<?> contrl) {
		try {
			try {
				Constructor<?> constructor = contrl
						.getConstructor(WebElement.class);
				return constructor.newInstance(element);
			} catch (NoSuchMethodException e) {
				return contrl.newInstance();
			}
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
}
