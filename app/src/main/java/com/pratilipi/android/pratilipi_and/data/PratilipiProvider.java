package com.pratilipi.android.pratilipi_and.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

/**
 * Created by Rahul Ranjan on 8/21/2015.
 */
public class PratilipiProvider extends ContentProvider {

    private static final String LOG_TAG = PratilipiProvider.class.getSimpleName();

    // The URI Matcher used by this content provider.
    private static final UriMatcher sUriMatcher = buildUriMatcher();
    private PratilipiDbHelper mOpenHelper;

    static final int HOMESCREEN = 100;
    static final int HOMESCREEN_CONTENT_BY_CATEGORY = 101;
    static final int USER = 200;
    static final int USER_BY_EMAIL = 201;

    private static final String sLanguageAndCategorySelection =
            PratilipiContract.HomeScreenEntity.COLUMN_CATEGORY_ID + "=?" +
                    PratilipiContract.HomeScreenEntity.COLUMN_LANGUAGE_ID + "=?";
    private static final String sUserByEmailSelection = PratilipiContract.UserEntity.COLUMN_EMAIL + "=?";

    static UriMatcher buildUriMatcher(){
        final UriMatcher uriMatcher = new UriMatcher( UriMatcher.NO_MATCH );
        final String pratilipiAuthority = PratilipiContract.CONTENT_AUTHORITY;

        uriMatcher.addURI(pratilipiAuthority, PratilipiContract.PATH_HOMESCREEN, HOMESCREEN);
        uriMatcher.addURI( pratilipiAuthority, PratilipiContract.PATH_HOMESCREEN + "/*/#", HOMESCREEN_CONTENT_BY_CATEGORY );
        uriMatcher.addURI( pratilipiAuthority, PratilipiContract.PATH_USER, USER );
        uriMatcher.addURI( pratilipiAuthority, PratilipiContract.PATH_USER + "/*", USER_BY_EMAIL );

        return uriMatcher;
    }


    @Override
    public boolean onCreate() {
        mOpenHelper = new PratilipiDbHelper(getContext());
        return true;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Cursor retCursor;
        Log.e(LOG_TAG, "URI : " + uri);
        Log.e(LOG_TAG, "URI MATCHED WITH : " + sUriMatcher.match(uri));
        switch (sUriMatcher.match(uri)){
            case HOMESCREEN_CONTENT_BY_CATEGORY: {
                retCursor = getContentByLanguageAndCategory(uri, projection, sortOrder);
                break;
            }
            case HOMESCREEN:{
                retCursor = getDistinctCategory(projection);
                break;
            }
            case USER: {
                retCursor =  getUser(uri, projection, selection, selectionArgs);
                break;
            }
            case USER_BY_EMAIL :{
                retCursor = getUserByEmail(uri, projection);
                break;
            }
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        return retCursor;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    private void normalizeDate(ContentValues values) {
        // normalize the date value
        if (values.containsKey(PratilipiContract.HomeScreenEntity.COLUMN_DATE)) {
            long dateValue = values.getAsLong(PratilipiContract.HomeScreenEntity.COLUMN_DATE);
            values.put(PratilipiContract.HomeScreenEntity.COLUMN_DATE, PratilipiContract.normalizeDate(dateValue));
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
        Uri returnUri = null;
        switch (match){
            case HOMESCREEN:{
                normalizeDate(values);
                long id = db.insert(PratilipiContract.HomeScreenEntity.TABLE_NAME, null, values);
                if( id > 0 )
                    Log.d(LOG_TAG, "Inserted Row Id : " + id);
                break;
            }
            case USER :{
                long id = db.insert(PratilipiContract.UserEntity.TABLE_NAME, null, values);
                if( id > 0 ){
                    returnUri = PratilipiContract.UserEntity.getUserUri(String.valueOf(id));
                    break;
                } else
                    Log.e(LOG_TAG, "User Insert Failed");
            }
            default:
                throw  new UnsupportedOperationException("Unknown uri: " + uri);
        }
        return returnUri;
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        switch (sUriMatcher.match(uri)){
            case HOMESCREEN: {
                db.beginTransaction();
                int rowsInserted = 0;
                try{
                    for( ContentValues value : values ){
                        normalizeDate(value);
                        long _id = db.insert(PratilipiContract.HomeScreenEntity.TABLE_NAME, null, value);
                        if (_id != -1) {
                            rowsInserted++;
                        }
                    }
                    db.setTransactionSuccessful();
                } finally {
                  db.endTransaction();
                }
                return rowsInserted;
            }
            default:
                return super.bulkInsert(uri, values);
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
        int rowsUpdated = 0;
        switch (match){
            case USER:{
                rowsUpdated = db.update(PratilipiContract.UserEntity.TABLE_NAME, values, selection, selectionArgs);
                Log.e(LOG_TAG, "Updated Id : " + rowsUpdated);
                break;
            }
            default: throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        return rowsUpdated;
    }

    public Cursor getDistinctCategory(String[] projection){
        SQLiteQueryBuilder contentByCategoryAndLanguage = new SQLiteQueryBuilder();
        contentByCategoryAndLanguage.setTables(PratilipiContract.HomeScreenEntity.TABLE_NAME);

        return contentByCategoryAndLanguage.query(
                mOpenHelper.getReadableDatabase(),
                projection,
                null,
                null,
                PratilipiContract.HomeScreenEntity.COLUMN_CATEGORY_ID,
                null,
                null);

    }

    private Cursor getContentByLanguageAndCategory( Uri uri, String[] projection, String sortOrder){

        String languageId = PratilipiContract.HomeScreenEntity.getLanguageIdFromUri(uri);
        String categoryId = PratilipiContract.HomeScreenEntity.getCategoryIdFromUri(uri);

        SQLiteQueryBuilder contentByCategoryAndLanguage = new SQLiteQueryBuilder();
        contentByCategoryAndLanguage.setTables(PratilipiContract.HomeScreenEntity.TABLE_NAME);

        return contentByCategoryAndLanguage.query( mOpenHelper.getReadableDatabase(),
                projection,
                sLanguageAndCategorySelection,
                new String[]{categoryId, languageId},
                null,
                null,
                null );
    }

    private Cursor getUser( Uri uri, String[] projection, String selection, String[] selectionArgs ){
        SQLiteQueryBuilder userQuery = new SQLiteQueryBuilder();
        userQuery.setTables(PratilipiContract.UserEntity.TABLE_NAME);

        return userQuery.query(mOpenHelper.getReadableDatabase(),
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null );
    }

    private Cursor getUserByEmail(Uri uri, String[] projection){

        String email = PratilipiContract.UserEntity.getEmailFromUri(uri);
        SQLiteQueryBuilder userQuery = new SQLiteQueryBuilder();
        userQuery.setTables(PratilipiContract.UserEntity.TABLE_NAME);

        return userQuery.query(mOpenHelper.getReadableDatabase(),
                projection,
                sUserByEmailSelection,
                new String[]{email},
                null,
                null,
                null );
    }

}
